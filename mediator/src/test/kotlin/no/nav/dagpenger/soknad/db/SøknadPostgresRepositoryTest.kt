package no.nav.dagpenger.soknad.db

import FerdigSøknadData
import de.slub.urn.URN
import io.mockk.every
import io.mockk.mockk
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.DeepEquals.assertDeepEquals
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Prosessnavn
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.mal.SøknadMal
import no.nav.dagpenger.soknad.mal.SøknadMalPostgresRepository
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class SøknadPostgresRepositoryTest {
    private val søknadId = UUID.randomUUID()
    private val språk = Språk("NO")
    private val dokumentFaktum =
        Faktum(faktumJson("1", "f1"))
    private val dokumentFaktum2 =
        Faktum(faktumJson("3", "f3"))
    private val faktaSomSannsynliggjøres =
        mutableSetOf(
            Faktum(faktumJson("2", "f2")),
        )
    private val faktaSomSannsynliggjøres2 =
        mutableSetOf(
            Faktum(faktumJson("4", "f4")),
        )
    private val sannsynliggjøring = Sannsynliggjøring(
        id = dokumentFaktum.id,
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres,
    )
    private val sannsynliggjøring2 = Sannsynliggjøring(
        id = dokumentFaktum2.id,
        faktum = dokumentFaktum2,
        sannsynliggjør = faktaSomSannsynliggjøres2,
    )
    private val krav = Krav(
        sannsynliggjøring,
    )
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
        dokumentkrav = Dokumentkrav.rehydrer(
            krav = setOf(krav),
        ),
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
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav),
            ),
            sistEndretAvBruker = now,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = prosessversjon,
            data = FerdigSøknadData,
        )

        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { repository ->
                søknad.håndterSlettSøknadHendelse(
                    SlettSøknadHendelse(
                        søknadId,
                        ident,
                    ),
                )
                repository.lagre(søknad)

                assertEquals(0, repository.hentSøknader(ident).size)
                assertNull(repository.hent(søknadId))
            }
        }
    }

    @Test
    fun `Lagre og hente søknad med dokumentkrav og aktivitetslogg`() {
        val krav1 = Krav(sannsynliggjøring)
        val krav2 = Krav(sannsynliggjøring2)
        val søknad = Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = opprettet,
            null,
            språk = språk,
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav1, krav2),
            ),
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
                assertAntallRader("dokumentkrav_v1", 2)
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
    fun `dokumentkrav som sannsynliggjøres skal lagres med samme struktur`() {
        val originalFaktumJson = faktumJson("1", "f1")
        val dokumentFaktum =
            Faktum(originalFaktumJson)
        val originalFaktumSomsannsynliggjøresFakta = faktumJson("2", "f2")
        val faktaSomSannsynliggjøres =
            mutableSetOf(
                Faktum(originalFaktumSomsannsynliggjøresFakta),
            )
        val sannsynliggjøring = Sannsynliggjøring(
            id = dokumentFaktum.id,
            faktum = dokumentFaktum,
            sannsynliggjør = faktaSomSannsynliggjøres,
        )
        val krav = Krav(
            sannsynliggjøring,
        )
        val søknad = Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = opprettet,
            null,
            språk = språk,
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav),
            ),
            sistEndretAvBruker = now.minusDays(1),
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = prosessversjon,
            data = FerdigSøknadData,
        )

        withMigratedDb {
            SøknadPostgresRepository(dataSource).let { søknadPostgresRepository ->
                søknadPostgresRepository.lagre(søknad)
                val dokumentkrav = hentDokumentKrav(søknadPostgresRepository.hent(søknadId)!!)
                val rehydrertSannsynliggjøring = dokumentkrav.aktiveDokumentKrav().first().sannsynliggjøring
                assertEquals(originalFaktumJson, rehydrertSannsynliggjøring.faktum().originalJson())
                assertEquals(
                    originalFaktumSomsannsynliggjøresFakta,
                    rehydrertSannsynliggjøring.sannsynliggjør().first().originalJson(),
                )
            }
        }
    }

    @Test
    fun `Kan hente ut søknad med dokumentasjonskrav svar`() {
        val tidspunkt = now
        val fil1 = Krav.Fil(
            filnavn = "ja.jpg",
            urn = URN.rfc8141().parse("urn:vedlegg:1111/12345"),
            storrelse = 50000,
            tidspunkt = tidspunkt,
            bundlet = false,
        )
        val fil2 = Krav.Fil(
            filnavn = "nei.jpg",
            urn = URN.rfc8141().parse("urn:vedlegg:1111/45678"),
            storrelse = 50000,
            tidspunkt = tidspunkt,
            bundlet = false,
        )
        withMigratedDb {
            val søknadPostgresRepository = SøknadPostgresRepository(dataSource)
            søknadPostgresRepository.lagre(søknad)
            val søknadMediator = SøknadMediator(
                rapidsConnection = mockk(relaxed = true),
                søknadDataRepository = mockk(),
                søknadMalRepository = mockk(),
                ferdigstiltSøknadRepository = mockk(),
                søknadRepository = søknadPostgresRepository,
                søknadObservers = listOf(),
                dokumentkravRepository = PostgresDokumentkravRepository(dataSource),
            )

            hentDokumentKrav(søknadMediator.hent(søknadId)!!).let {
                it.aktiveDokumentKrav().forEach { krav ->
                    assertTrue(krav.svar.filer.isEmpty())
                    assertEquals(Krav.Svar.SvarValg.IKKE_BESVART, krav.svar.valg)
                    assertNull(krav.svar.begrunnelse)
                }
            }

            søknadMediator.behandleLeggTilFil(
                LeggTilFil(
                    søknadId,
                    ident,
                    "1",
                    fil1,
                ),
            )

            søknadMediator.behandleLeggTilFil(
                LeggTilFil(
                    søknadId,
                    ident,
                    "1",
                    fil2,
                ),
            )
            søknadMediator.behandleLeggTilFil(
                LeggTilFil(
                    søknadId,
                    ident,
                    "1",
                    fil2,
                ),
            )
            hentDokumentKrav(søknadMediator.hent(søknadId)!!).let {
                assertEquals(2, it.aktiveDokumentKrav().first().svar.filer.size)
                it.aktiveDokumentKrav().first().svar.filer.forEach { kravFil ->
                    assertFalse(kravFil.bundlet)
                }
                it.aktiveDokumentKrav().forEach { krav ->
                    assertEquals(Krav.Svar.SvarValg.SEND_NÅ, krav.svar.valg)
                    assertNull(krav.svar.begrunnelse)
                }
            }

            søknadMediator.behandleDokumentKravSammenstilling(
                DokumentKravSammenstilling(
                    søknadID = søknadId,
                    ident = ident,
                    kravId = "1",
                    urn = URN.rfc8141().parse("urn:vedlegg:bundle"),
                ),
            )

            hentDokumentKrav(søknadMediator.hent(søknadId)!!).let { dokumentkrav ->
                val filer = dokumentkrav.aktiveDokumentKrav().first().svar.filer
                assertEquals(2, filer.size)
                filer.forEach { fil ->
                    assertTrue(fil.bundlet)
                }
            }

            søknadMediator.behandleDokumentasjonIkkeTilgjengelig(
                DokumentasjonIkkeTilgjengelig(
                    søknadId,
                    ident,
                    "1",
                    valg = Krav.Svar.SvarValg.SEND_SENERE,
                    begrunnelse = "Har ikke",
                ),
            )

            hentDokumentKrav(søknadMediator.hent(søknadId)!!).let {
                assertEquals(2, it.aktiveDokumentKrav().first().svar.filer.size)
                it.aktiveDokumentKrav().forEach { krav ->
                    assertEquals(Krav.Svar.SvarValg.SEND_SENERE, krav.svar.valg)
                    assertEquals("Har ikke", krav.svar.begrunnelse)
                }
            }

            søknadMediator.behandleSlettFil(
                SlettFil(
                    søknadID = søknadId,
                    ident = ident,
                    kravId = "1",
                    urn = fil1.urn,
                ),
            )
            assertEquals(
                1,
                hentDokumentKrav(søknadMediator.hent(søknadId)!!).aktiveDokumentKrav().first().svar.filer.size,
            )

            søknadMediator.behandleSlettFil(
                SlettFil(
                    søknadID = søknadId,
                    ident = ident,
                    kravId = "1",
                    urn = fil2.urn,
                ),
            )

            assertEquals(
                0,
                hentDokumentKrav(søknadMediator.hent(søknadId)!!).aktiveDokumentKrav().first().svar.filer.size,
            )

            assertDoesNotThrow {
                søknadMediator.behandleSlettFil(
                    SlettFil(
                        søknadID = søknadId,
                        ident = ident,
                        kravId = "1",
                        urn = fil2.urn,
                    ),
                )
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
                generellInnsending,
            )
            every { it.hentSøknader(ident2) } returns setOf(
                generellInnsending,
            )
            every { it.hentPåbegyntSøknad(any()) } answers { callOriginal() }
        }

        assertNotNull(søknadPostgresRepository.hentPåbegyntSøknad(ident))
        assertEquals(søknadPostgresRepository.hentPåbegyntSøknad(ident)!!.søknadUUID(), søknadId)

        assertNull(søknadPostgresRepository.hentPåbegyntSøknad(ident2))
    }

    private fun søknadMedProssess(prosessNavn: String) =
        Søknad.rehydrer(
            søknadId = søknadId,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            null,
            språk = Språk("NO"),
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav),
            ),
            sistEndretAvBruker = now,
            tilstandsType = Påbegynt,
            aktivitetslogg = Aktivitetslogg(),
            prosessversjon = Prosessversjon(Prosessnavn(prosessNavn), 2),
            data = FerdigSøknadData,
        )

    @Test
    fun `Skal kunne hente ut en påbegynt søknad`() {
        val søknadIdForInnsendt = UUID.randomUUID()
        val innsendtSøknad = Søknad.rehydrer(
            søknadId = søknadIdForInnsendt,
            ident = ident,
            opprettet = ZonedDateTime.now(),
            null,
            språk = Språk("NO"),
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav),
            ),
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
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav),
            ),
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
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav),
            ),
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
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav),
            ),
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
            dokumentkrav = Dokumentkrav.rehydrer(
                krav = setOf(krav),
            ),
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
                        "Bare søknader uten versjon eller lavere versjon skal migreres",
                    )
                }
            }
        }
    }

    private fun hentDokumentKrav(søknad: Søknad): Dokumentkrav {
        return TestSøknadVisitor(søknad).dokumentKrav
    }

    private fun assertAntallRader(tabell: String, antallRader: Int) {
        val faktiskeRader = using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("select count(1) from $tabell").map { row ->
                    row.int(1)
                }.asSingle,
            )
        }
        assertEquals(antallRader, faktiskeRader, "Feil antall rader for tabell: $tabell")
    }
}
