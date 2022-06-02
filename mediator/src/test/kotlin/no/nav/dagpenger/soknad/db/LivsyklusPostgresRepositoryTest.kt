package no.nav.dagpenger.soknad.db

import com.fasterxml.jackson.module.kotlin.contains
import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.LivsyklusPostgresRepository.PersistentSøkerOppgave
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import no.nav.dagpenger.soknad.mottak.SøkerOppgave
import no.nav.dagpenger.soknad.serder.objectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime
import java.util.UUID

internal class LivsyklusPostgresRepositoryTest {
    @Test
    fun `Lagre og hente person uten søknader`() {
        withMigratedDb {
            LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                val expectedPerson = Person("12345678910")
                it.lagre(expectedPerson)
                val person = it.hent("12345678910")

                assertNotNull(person)
                assertEquals(expectedPerson, person)

                assertDoesNotThrow {
                    it.lagre(expectedPerson)
                }
            }
        }
    }

    @Test
    fun `Invaliderer riktig`() {
        withMigratedDb {
            val livssyklusRepository = LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource)
            val søknadUuid = UUID.randomUUID()
            val søknadUuid2 = UUID.randomUUID()
            val eier = "12345678901"
            val eier2 = "12345678902"
            val søknad = PersistentSøkerOppgave(søknad(søknadUuid, fødselsnummer = eier))
            val søknad2 = PersistentSøkerOppgave(søknad(søknadUuid2, fødselsnummer = eier2))

            livssyklusRepository.invalider(søknadUuid, eier)
            assertAntallRader(tabell = "soknad_cache", antallRader = 0)

            livssyklusRepository.lagre(søknad)
            livssyklusRepository.lagre(søknad2)

            assertAntallRader(tabell = "soknad_cache", antallRader = 2)

            livssyklusRepository.invalider(søknadUuid, eier)
            assertAntallRader(tabell = "soknad_cache", antallRader = 1)
        }
    }

    @Test
    fun `Hent person som ikke finnes`() {
        withMigratedDb {
            LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                assertNull(it.hent("finnes ikke"))
            }
        }
    }

    @Test
    fun `Lagre og hente person med søknader, dokumenter og aktivitetslogg`() {
        val søknadId1 = UUID.randomUUID()
        val søknadId2 = UUID.randomUUID()
        val originalPerson = Person("12345678910") {
            mutableListOf(
                Søknad(søknadId1, it),
                Søknad.rehydrer(
                    søknadId = søknadId2,
                    person = it,
                    tilstandsType = "Journalført",
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
                    innsendtTidspunkt = ZonedDateTime.now()
                )
            )
        }

        withMigratedDb {
            LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                it.lagre(originalPerson)
                val personFraDatabase = it.hent("12345678910")
                assertNotNull(personFraDatabase)
                assertEquals(originalPerson, personFraDatabase)

                val fraDatabaseVisitor = TestPersonVisitor(personFraDatabase)
                val originalVisitor = TestPersonVisitor(originalPerson)
                val søknaderFraDatabase = fraDatabaseVisitor.søknader
                val originalSøknader = originalVisitor.søknader
                assertNull(fraDatabaseVisitor.dokumenter.get(søknadId1))
                assertEquals(2, fraDatabaseVisitor.dokumenter[søknadId2]?.varianter?.size)
                assertDeepEquals(originalSøknader.first(), søknaderFraDatabase.first())
                assertDeepEquals(originalSøknader.last(), søknaderFraDatabase.last())

                assertAntallRader("aktivitetslogg_v1", 1)
                assertEquals(originalVisitor.aktivitetslogg.toString(), fraDatabaseVisitor.aktivitetslogg.toString())
            }
        }
    }

    @Test
    fun `Henter påbegynte søknader`() {
        val person = Person("12345678910") {
            mutableListOf(
                Søknad.rehydrer(
                    søknadId = UUID.randomUUID(),
                    person = it,
                    tilstandsType = "Påbegynt",
                    dokument = Søknad.Dokument(varianter = emptyList()),
                    journalpostId = "jouhasjk",
                    innsendtTidspunkt = ZonedDateTime.now()
                ),
                Søknad.rehydrer(
                    søknadId = UUID.randomUUID(),
                    person = it,
                    tilstandsType = "Journalført",
                    dokument = Søknad.Dokument(varianter = emptyList()),
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = ZonedDateTime.now()
                )
            )
        }

        withMigratedDb {
            LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                it.lagre(person)
                assertEquals(1, it.hentPåbegynte(person.ident()).size)

                assertEquals(0, it.hentPåbegynte("hubbba").size)
            }
        }
    }

    @Test
    fun `Kan oppdatere søknader`() {
        val søknadId = UUID.randomUUID()
        val originalPerson = Person("12345678910") {
            mutableListOf(
                Søknad(søknadId, it),
                Søknad.rehydrer(
                    søknadId = søknadId,
                    person = it,
                    tilstandsType = "Journalført",
                    dokument = Søknad.Dokument(varianter = emptyList()),
                    journalpostId = "journalpostid",
                    innsendtTidspunkt = ZonedDateTime.now()
                )
            )
        }

        withMigratedDb {
            LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                it.lagre(originalPerson)
                val personFraDatabase = it.hent("12345678910")
                assertNotNull(personFraDatabase)

                val søknaderFraDatabase = TestPersonVisitor(personFraDatabase).søknader
                assertEquals(1, søknaderFraDatabase.size)
            }
        }
    }

    private fun assertDeepEquals(expected: Søknad, result: Søknad) {
        assertTrue(expected.deepEquals(result), "Søknadene var ikke like")
    }

    private class TestPersonVisitor(person: Person?) : PersonVisitor {
        val dokumenter: MutableMap<UUID, Søknad.Dokument> = mutableMapOf()
        lateinit var søknader: List<Søknad>
        lateinit var aktivitetslogg: Aktivitetslogg

        init {
            person?.accept(this)
        }

        override fun visitPerson(ident: String, søknader: List<Søknad>) {
            this.søknader = søknader
        }

        override fun visitSøknad(
            søknadId: UUID,
            person: Person,
            tilstand: Søknad.Tilstand,
            dokument: Søknad.Dokument?,
            journalpostId: String?,
            innsendtTidspunkt: ZonedDateTime?
        ) {
            dokument?.let { dokumenter[søknadId] = it }
        }

        override fun postVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
            this.aktivitetslogg = aktivitetslogg
        }
    }

    private fun assertAntallRader(tabell: String, antallRader: Int) {
        val faktiskeRader = using(sessionOf(PostgresDataSourceBuilder.dataSource)) { session ->
            session.run(
                queryOf("select count(1) from $tabell").map { row ->
                    row.int(1)
                }.asSingle
            )
        }
        assertEquals(antallRader, faktiskeRader, "Feil antall rader for tabell: $tabell")
    }

    @Test
    fun `Lagre søknad og hente`() {
        withMigratedDb {
            val postgresPersistence = LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource)
            val søknadUuid = UUID.randomUUID()
            val søknad = PersistentSøkerOppgave(søknad(søknadUuid))
            postgresPersistence.lagre(søknad)

            val rehydrertSøknad = postgresPersistence.hent(søknadUuid)
            assertEquals(søknad.søknadUUID(), rehydrertSøknad.søknadUUID())
            assertEquals(søknad.eier(), rehydrertSøknad.eier())
            assertEquals(søknad.asFrontendformat(), rehydrertSøknad.asFrontendformat())
        }
    }

    @Test
    fun `Lagre samme søknad id flere ganger appendes på raden, men siste versjon av søknad hentes`() {
        val søknadUuid = UUID.randomUUID()
        withMigratedDb {
            val postgresPersistence = LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource)
            postgresPersistence.lagre(PersistentSøkerOppgave(søknad(søknadUuid)))
            postgresPersistence.lagre(
                PersistentSøkerOppgave(
                    søknad(
                        søknadUuid,
                        seksjoner = "oppdatert første gang"
                    )
                )
            )
            postgresPersistence.lagre(
                PersistentSøkerOppgave(
                    søknad(
                        søknadUuid,
                        seksjoner = "oppdatert andre gang"
                    )
                )
            )
            val rehydrertSøknad = postgresPersistence.hent(søknadUuid)
            assertEquals(søknadUuid, rehydrertSøknad.søknadUUID())
            assertEquals("12345678910", rehydrertSøknad.eier())
            assertEquals(
                "oppdatert andre gang",
                rehydrertSøknad.asFrontendformat()[SøkerOppgave.Keys.SEKSJONER].asText()
            )
        }
    }

    @Test
    fun `Henter en søknad som ikke finnes`() {
        withMigratedDb {
            val postgresPersistence = LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource)
            assertThrows<NotFoundException> { postgresPersistence.hent(UUID.randomUUID()) }
        }
    }

    @Test
    fun `Fødselsnummer skal ikke komme med som en del av frontendformatet, men skal fortsatt være en del av søknaden`() {
        val søknadJson = søknad(UUID.randomUUID())
        val søknad = PersistentSøkerOppgave(søknadJson)

        val frontendformat = søknad.asFrontendformat()
        Assertions.assertFalse(frontendformat.contains(SøkerOppgave.Keys.FØDSELSNUMMER))
        assertNotNull(søknad.eier())
    }

    private fun søknad(søknadUuid: UUID, seksjoner: String = "seksjoner", fødselsnummer: String = "12345678910") = objectMapper.readTree(
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
