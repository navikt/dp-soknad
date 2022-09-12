package no.nav.dagpenger.soknad.livssyklus

import com.fasterxml.jackson.module.kotlin.contains
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Journalført
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknadhåndterer
import no.nav.dagpenger.soknad.SøknadhåndtererVisitor
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository.PersistentSøkerOppgave
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

internal class LivssyklusPostgresRepositoryTest {
    private val språk = Språk("NO")
    private val dokumentFaktum =
        Faktum(faktumJson("1", "f1"))
    private val faktaSomSannsynliggjøres =
        mutableSetOf(
            Faktum(faktumJson("2", "f2"))
        )
    private val sannsynliggjøring = Sannsynliggjøring(
        id = dokumentFaktum.id,
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres
    )
    private val krav = Krav(
        sannsynliggjøring
    )

    @Test
    fun `Lagre og hente person uten søknader`() {
        withMigratedDb {
            LivssyklusPostgresRepository(dataSource).let {
                val expectedSøknadhåndterer = Søknadhåndterer()
                it.lagre(expectedSøknadhåndterer, "12345678910")
                val søknadhåndterer: Søknadhåndterer? = it.hent("12345678910")

                assertNotNull(søknadhåndterer)
                assertTrue(TestSøknadhåndtererVisitor(søknadhåndterer).søknader.isEmpty())

                assertDoesNotThrow {
                    it.lagre(expectedSøknadhåndterer, "12345678910")
                }
            }
        }
    }

    @Test
    fun `Hent person som ikke finnes`() {
        withMigratedDb {
            LivssyklusPostgresRepository(dataSource).let {
                assertNull(it.hent("finnes ikke"))
            }
        }
    }

    @Test
    fun `Lagre og hente person med søknader, dokumenter, dokumentkrav og aktivitetslogg`() {
        val søknadId1 = UUID.randomUUID()
        val søknadId2 = UUID.randomUUID()
        val originalSøknadhåndterer = Søknadhåndterer {
            mutableListOf(
                Søknad(søknadId1, språk, "12345678910"),
                Søknad.rehydrer(
                    søknadId = søknadId2,
                    ident = "12345678910",
                    dokument = Søknad.Dokument(
                        varianter = listOf(
                            Søknad.Dokument.Variant(
                                urn = "urn:soknad:fil1",
                                format = "ARKIV",
                                type = "PDF"
                            ),
                            Søknad.Dokument.Variant(
                                urn = "urn:soknad:fil2",
                                format = "ARKIV",
                                type = "PDF"
                            )
                        )
                    ),
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = ZonedDateTime.now(),
                    språk = språk,
                    dokumentkrav = Dokumentkrav.rehydrer(
                        krav = setOf(krav)
                    ),
                    sistEndretAvBruker = ZonedDateTime.now().minusDays(1),
                    tilstandsType = Journalført,
                    aktivitetslogg = Aktivitetslogg()
                )
            )
        }

        withMigratedDb {
            LivssyklusPostgresRepository(dataSource).let {
                it.lagre(originalSøknadhåndterer, "12345678910")
                val søknadhåndtererFraDatabase = it.hent("12345678910", true)
                assertNotNull(søknadhåndtererFraDatabase)
                val fraDatabaseVisitor = TestSøknadhåndtererVisitor(søknadhåndtererFraDatabase)
                val originalVisitor = TestSøknadhåndtererVisitor(originalSøknadhåndterer)
                val søknaderFraDatabase = fraDatabaseVisitor.søknader
                val originalSøknader = originalVisitor.søknader
                assertNull(fraDatabaseVisitor.dokumenter[søknadId1])
                assertEquals(2, fraDatabaseVisitor.dokumenter[søknadId2]?.varianter?.size)
                assertDeepEquals(originalSøknader.first(), søknaderFraDatabase.first())
                assertDeepEquals(originalSøknader.last(), søknaderFraDatabase.last())

                assertAntallRader("aktivitetslogg_v3", 2)
                assertAntallRader("dokumentkrav_v1", 1)
                assertEquals(originalVisitor.aktivitetslogg.toString(), fraDatabaseVisitor.aktivitetslogg.toString())
            }
        }
    }

    @Test
    fun `upsert på dokumentasjonskrav`() {
        val søknadId = UUID.randomUUID()
        val originalSøknadhåndterer = Søknadhåndterer {
            mutableListOf(
                Søknad.rehydrer(
                    søknadId = søknadId,
                    ident = "12345678910",
                    dokument = Søknad.Dokument(
                        varianter = listOf(
                            Søknad.Dokument.Variant(
                                urn = "urn:soknad:fil1",
                                format = "ARKIV",
                                type = "PDF"
                            ),
                            Søknad.Dokument.Variant(
                                urn = "urn:soknad:fil2",
                                format = "ARKIV",
                                type = "PDF"
                            )
                        )
                    ),
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = ZonedDateTime.now(),
                    språk = språk,
                    dokumentkrav = Dokumentkrav.rehydrer(
                        krav = setOf(krav)
                    ),
                    sistEndretAvBruker = ZonedDateTime.now(),
                    tilstandsType = Journalført,
                    aktivitetslogg = Aktivitetslogg()

                )
            )
        }
        withMigratedDb {
            LivssyklusPostgresRepository(dataSource).let {
                it.lagre(originalSøknadhåndterer, "12345678910")
                it.lagre(originalSøknadhåndterer, "12345678910")
            }
        }
    }

    @Test
    fun `Henter påbegynte søknader`() {
        val påbegyntSøknadUuid = UUID.randomUUID()
        val innsendtTidspunkt = ZonedDateTime.now()
        val søknadhåndterer = Søknadhåndterer {
            mutableListOf(
                Søknad.rehydrer(
                    søknadId = påbegyntSøknadUuid,
                    ident = "12345678910",
                    dokument = Søknad.Dokument(varianter = emptyList()),
                    journalpostId = "jouhasjk",
                    innsendtTidspunkt = innsendtTidspunkt,
                    språk = språk,
                    dokumentkrav = Dokumentkrav(),
                    sistEndretAvBruker = innsendtTidspunkt,
                    tilstandsType = Påbegynt,
                    aktivitetslogg = Aktivitetslogg()

                ),
                Søknad.rehydrer(
                    søknadId = UUID.randomUUID(),
                    ident = "12345678910",
                    dokument = Søknad.Dokument(varianter = emptyList()),
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = innsendtTidspunkt,
                    språk = språk,
                    dokumentkrav = Dokumentkrav(),
                    sistEndretAvBruker = innsendtTidspunkt,
                    tilstandsType = Journalført,
                    aktivitetslogg = Aktivitetslogg()

                )
            )
        }

        withMigratedDb {
            LivssyklusPostgresRepository(dataSource).let {
                it.lagre(søknadhåndterer, "12345678910")
                val påbegyntSøknad = it.hentPåbegyntSøknad("12345678910")!!
                assertEquals(påbegyntSøknadUuid, påbegyntSøknad.uuid)
                assertEquals(LocalDate.from(innsendtTidspunkt), påbegyntSøknad.startDato)

                assertEquals(null, it.hentPåbegyntSøknad("hubbba"))
            }
        }
    }

    @Test
    fun `Kan oppdatere søknader`() {
        val søknadId = UUID.randomUUID()
        val originalSøknadhåndterer = Søknadhåndterer {
            mutableListOf(
                Søknad(søknadId, språk, "12345678910"),
                Søknad.rehydrer(
                    søknadId = søknadId,
                    ident = "12345678910",
                    dokument = Søknad.Dokument(varianter = emptyList()),
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = ZonedDateTime.now(),
                    språk = språk,
                    dokumentkrav = Dokumentkrav(),
                    sistEndretAvBruker = ZonedDateTime.now(),
                    tilstandsType = Journalført,
                    aktivitetslogg = Aktivitetslogg()

                )
            )
        }

        withMigratedDb {
            LivssyklusPostgresRepository(dataSource).let {
                it.lagre(originalSøknadhåndterer, "12345678910")
                val personFraDatabase = it.hent("12345678910")
                assertNotNull(personFraDatabase)
                val søknaderFraDatabase = TestSøknadhåndtererVisitor(personFraDatabase).søknader
                assertEquals(1, søknaderFraDatabase.size)
            }
        }
    }

    @Test
    fun `Fødselsnummer skal ikke komme med som en del av frontendformatet, men skal fortsatt være en del av søknaden`() {
        val søknadJson = søknad(UUID.randomUUID())
        val søknad = PersistentSøkerOppgave(søknadJson)
        val frontendformat = søknad.asFrontendformat()
        assertFalse(frontendformat.contains(SøkerOppgave.Keys.FØDSELSNUMMER))
        assertNotNull(søknad.eier())
    }

    private fun assertDeepEquals(expected: Søknad, result: Søknad) {
        assertTrue(expected.deepEquals(result), "Søknadene var ikke like")
    }

    private class TestSøknadhåndtererVisitor(søknadhåndterer: Søknadhåndterer?) : SøknadhåndtererVisitor {
        val dokumenter: MutableMap<UUID, Søknad.Dokument> = mutableMapOf()
        val dokumentkrav: MutableMap<UUID, Dokumentkrav> = mutableMapOf()
        lateinit var søknader: List<Søknad>
        lateinit var aktivitetslogg: Aktivitetslogg

        init {
            søknadhåndterer?.accept(this)
        }

        override fun visitSøknader(søknader: List<Søknad>) {
            this.søknader = søknader
        }

        override fun visitSøknad(
            søknadId: UUID,
            ident: String,
            tilstand: Søknad.Tilstand,
            dokument: Søknad.Dokument?,
            journalpostId: String?,
            innsendtTidspunkt: ZonedDateTime?,
            språk: Språk,
            dokumentkrav: Dokumentkrav,
            sistEndretAvBruker: ZonedDateTime?
        ) {
            dokument?.let { dokumenter[søknadId] = it }
            this.dokumentkrav[søknadId] = dokumentkrav
        }

        override fun postVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
            this.aktivitetslogg = aktivitetslogg
        }
    }

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

    private fun søknad(søknadUuid: UUID, seksjoner: String = "seksjoner", fødselsnummer: String = "12345678910") =
        objectMapper.readTree(
            """{
  "@event_name": "søker_oppgave",
  "fødselsnummer": $fødselsnummer,
  "versjon_id": 0,
  "versjon_navn": "test",
  "@opprettet": "2022-05-13T14:48:09.059643",
  "@id": "76be48d5-bb43-45cf-8d08-98206d0b9bd1",
  "søknad_uuid": "$søknadUuid",
  "ferdig": false,
  "seksjoner": "$seksjoner"
}"""
        )
}
