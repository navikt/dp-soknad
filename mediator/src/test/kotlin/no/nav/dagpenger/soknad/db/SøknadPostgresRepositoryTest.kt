package no.nav.dagpenger.soknad.db

import FerdigSøknadData
import io.mockk.every
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.DeepEquals.assertDeepEquals
import no.nav.dagpenger.soknad.Prosessnavn
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.mal.SøknadMal
import no.nav.dagpenger.soknad.mal.SøknadMalPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class SøknadPostgresRepositoryTest {
    private val søknadId = UUID.randomUUID()
    private val språk = Språk("NO")
    private val opprettet = ZonedDateTime.now(ZoneId.of("Europe/Oslo")).truncatedTo(ChronoUnit.SECONDS)
    private val now = ZonedDateTime.now(ZoneId.of("Europe/Oslo")).truncatedTo(ChronoUnit.SECONDS)
    val ident = "12345678910"
    private val prosessversjon = Prosessversjon("Dagpenger", 1)
    private val mal = SøknadMal(prosessversjon, objectMapper.createObjectNode())

    val søknad = Søknad.rehydrer(
        søknadId = søknadId,
        ident = ident,
        opprettet = opprettet,
        innsendt = now,
        språk = Språk("NO"),
        sistEndretAvBruker = now,
        tilstandsType = Påbegynt,
        aktivitetslogg = Aktivitetslogg(),
        prosessversjon = prosessversjon,
        data = FerdigSøknadData,
    )

    @Test
    fun `Henting av eier til søknad`() {
        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { repository ->
                repository.lagre(søknad)

                assertEquals(ident, repository.hentEier(søknad.søknadUUID()))
                assertNull(repository.hentEier(UUID.randomUUID()))
            }
        }
    }

    @Test
    fun `Lagring og henting av søknad med innsendt dato`() {
        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { repository ->
                repository.lagre(søknad)
                val søknad = repository.hent(søknadId)
                requireNotNull(søknad)

                assertEquals(now, TestSøknadVisitor(søknad).innsendt)
            }
        }
    }

    @Test
    fun `Skal ikke hente søknader som har tilstand slettet`() {
        val søknad = Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = opprettet,
            null,
            språk = Språk("NO"),
            sistEndretAvBruker = now,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = prosessversjon,
            data = FerdigSøknadData,
        )

        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { repository ->
                søknad.håndter(
                    SlettSøknadHendelse(
                        søknadId,
                        ident
                    )
                )
                repository.lagre(søknad)

                assertEquals(0, repository.hentSøknader(ident).size)
                assertNull(repository.hent(søknadId))
            }
        }
    }

    @Test
    fun `Lagre og hente søknad med aktivitetslogg`() {
        val søknad = Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = opprettet,
            null,
            språk = språk,
            sistEndretAvBruker = now.minusDays(1),
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = prosessversjon,
            data = FerdigSøknadData,
        )

        withMigratedDb {
            SøknadMalPostgresRepository(dataSource).lagre(mal)
            SøknadPostgresRepository(dataSource).let { søknadPostgresRepository ->
                søknadPostgresRepository.lagre(søknad)

                assertAntallRader("soknad_v1", 1)
                assertAntallRader("aktivitetslogg_v1", 1)

                søknadPostgresRepository.hent(søknadId).let { rehydrertSøknad ->
                    assertNotNull(rehydrertSøknad)
                    assertDeepEquals(rehydrertSøknad, søknad)
                }

                søknadPostgresRepository.lagre(søknad)

                søknadPostgresRepository.hent(søknadId).let { rehydrertSøknad ->
                    assertNotNull(rehydrertSøknad)
                    assertDeepEquals(rehydrertSøknad, søknad)
                }
            }
        }
    }

    @Test
    fun `Hent påbegynt søknad henter kun ut dagpenger søknad`() {
        val ident = "12345678901"
        val ident2 = "1234567890n2"
        val dagpengerSøknad = søknadMedProssess("Dagpenger")
        val generellInnsending = søknadMedProssess("Innsending")
        val søknadPostgresRepository = mockk<SøknadPostgresRepository>().also {
            every { it.hentSøknader(ident) } returns setOf(
                dagpengerSøknad,
                generellInnsending
            )
            every { it.hentSøknader(ident2) } returns setOf(
                generellInnsending
            )
            every { it.hentPåbegyntSøknad(any()) } answers { callOriginal() }
        }

        assertNotNull(søknadPostgresRepository.hentPåbegyntSøknad(ident))
        assertEquals(søknadPostgresRepository.hentPåbegyntSøknad(ident)!!.søknadUUID(), søknadId)

        assertNull(søknadPostgresRepository.hentPåbegyntSøknad(ident2))
    }

    @Test
    fun `Skal kunne hente ut en påbegynt søknad`() {
        val søknadIdForInnsendt = UUID.randomUUID()
        val innsendtSøknad = Søknad.rehydrer(
            søknadId = søknadIdForInnsendt,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            null,
            språk = Språk("NO"),
            sistEndretAvBruker = now,
            tilstandsType = Innsendt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = prosessversjon,
            data = FerdigSøknadData,
        )

        withMigratedDb {
            SøknadMalPostgresRepository(dataSource).lagre(mal)

            SøknadPostgresRepository(dataSource).let { repository ->
                repository.lagre(innsendtSøknad)
                repository.lagre(søknad)
                val hentetPåbegyntSøknad = repository.hentPåbegyntSøknad(ident)
                assertNotNull(hentetPåbegyntSøknad)
                assertDeepEquals(søknad, hentetPåbegyntSøknad)
            }
        }
    }

    @Test
    fun `Skal kunne hente ut alle påbegynte søknader`() {
        val søknadIdForPreMigrert = UUID.randomUUID()
        val søknadIdForPåbegynt = UUID.randomUUID()
        val søknadIdForNy = UUID.randomUUID()
        val søknadIdForInnsendt = UUID.randomUUID()
        val prosessversjon2 = Prosessversjon("Dagpenger", 2)
        val nyMal = SøknadMal(prosessversjon2, objectMapper.createObjectNode())
        val preMigrertSøknad = Søknad.rehydrer(
            søknadId = søknadIdForPreMigrert,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            null,
            språk = Språk("NO"),
            sistEndretAvBruker = now,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = null,
            data = FerdigSøknadData,
        )
        val påbegyntSøknad = Søknad.rehydrer(
            søknadId = søknadIdForPåbegynt,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            null,
            språk = Språk("NO"),
            sistEndretAvBruker = now,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = prosessversjon,
            data = FerdigSøknadData,
        )
        val nySøknad = Søknad.rehydrer(
            søknadId = søknadIdForNy,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            null,
            språk = Språk("NO"),
            sistEndretAvBruker = now,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = prosessversjon2,
            data = FerdigSøknadData,
        )
        val innsendtSøknad = Søknad.rehydrer(
            søknadId = søknadIdForInnsendt,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            null,
            språk = Språk("NO"),
            sistEndretAvBruker = now,
            tilstandsType = Innsendt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = prosessversjon,
            data = FerdigSøknadData,
        )

        withMigratedDb {
            SøknadMalPostgresRepository(dataSource).let { søknadMalPostgresRepository ->
                søknadMalPostgresRepository.lagre(mal)
                søknadMalPostgresRepository.lagre(nyMal)
            }
            SøknadPostgresRepository(dataSource).let { repository ->
                repository.lagre(preMigrertSøknad)
                repository.lagre(påbegyntSøknad)
                repository.lagre(nySøknad)
                repository.lagre(innsendtSøknad)

                with(repository.hentPåbegynteSøknader(Prosessversjon("Dagpenger", 2)).map { it.søknadUUID() }) {
                    assertEquals(2, this.size)

                    assertFalse(this.contains(søknadIdForInnsendt), "Innsendte blir ikke migrert")
                    assertEquals(
                        listOf(søknadIdForPreMigrert, søknadIdForPåbegynt),
                        this,
                        "Bare søknader uten versjon eller lavere versjon skal migreres"
                    )
                }
            }
        }
    }

    private fun søknadMedProssess(prosessNavn: String) =
        Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            null,
            språk = Språk("NO"),
            sistEndretAvBruker = now,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = Prosessversjon(Prosessnavn(prosessNavn), 2),
            data = FerdigSøknadData,
        )

    private fun assertAntallRader(tabell: String, antallRader: Int) {
        val faktiskeRader = using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("select count(1) from $tabell").map { row ->
                    row.int(1)
                }.asSingle
            )
        }
        assertEquals(antallRader, faktiskeRader, "Feil antall rader for tabell: $tabell")
    }
}
