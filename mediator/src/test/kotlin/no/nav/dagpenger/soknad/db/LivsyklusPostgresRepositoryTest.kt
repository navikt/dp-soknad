package no.nav.dagpenger.soknad.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

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
    fun `Hent person som ikke finnes`() {
        withMigratedDb {
            LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                assertNull(it.hent("finnes ikke"))
            }
        }
    }

    @Test
    fun `Lagre og hente person med søknader og aktivitetslogg`() {
        val originalPerson = Person("12345678910") {
            mutableListOf(
                Søknad(UUID.randomUUID(), it),
                Søknad.rehydrer(
                    søknadId = UUID.randomUUID(),
                    person = it,
                    tilstandsType = "Journalført",
                    dokumentLokasjon = "urn:hubba:bubba",
                    journalpostId = "journalpostid"
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
                    dokumentLokasjon = "urn:hubba:la",
                    journalpostId = "jouhasjk"
                ),
                Søknad.rehydrer(
                    søknadId = UUID.randomUUID(),
                    person = it,
                    tilstandsType = "Journalført",
                    dokumentLokasjon = "urn:hubba:bubba",
                    journalpostId = "journalpostid"
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
    fun `Sletter gamle søknader etter gitt tidsinterval`() {
        val påbegyntSøknadGammel = UUID.randomUUID()
        val påbegyntSøknadIdNy = UUID.randomUUID()
        val journalførtSøknadId = UUID.randomUUID()
        val testPersonIdent = "12345678910"
        val person = Person(testPersonIdent) {
            mutableListOf(
                Søknad.rehydrer(
                    søknadId = påbegyntSøknadGammel,
                    person = it,
                    tilstandsType = "Påbegynt",
                    dokumentLokasjon = "urn:hubba:la",
                    journalpostId = "1456"
                ),
                Søknad.rehydrer(
                    søknadId = påbegyntSøknadIdNy,
                    person = it,
                    tilstandsType = "Påbegynt",
                    dokumentLokasjon = "urn:hubba:la",
                    journalpostId = "jouhasjk"
                ),
                Søknad.rehydrer(
                    søknadId = journalførtSøknadId,
                    person = it,
                    tilstandsType = "Journalført",
                    dokumentLokasjon = "urn:hubba:bubba",
                    journalpostId = "journalpostid"
                )
            )
        }

        withMigratedDb {
            val nå = LocalDateTime.now()
            LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                it.lagre(person)
                editoOprettet(påbegyntSøknadGammel, nå.minusDays(30), PostgresDataSourceBuilder.dataSource)
                editoOprettet(journalførtSøknadId, nå.minusDays(30), PostgresDataSourceBuilder.dataSource)
                it.slettPåbegynteSøknaderEldreEnn(nå.minusDays(7L)).also { rader ->
                    assertEquals(1, rader)
                }
                it.hent(person.ident()).also { oppdatertPerson ->
                    assertEquals(2, TestPersonVisitor(oppdatertPerson).søknader.size)
                }
                it.hentPåbegynte(person.ident()).also { påbegynteSøknader ->
                    assertEquals(1, påbegynteSøknader.size)
                    assertEquals(påbegyntSøknadIdNy, påbegynteSøknader.first().uuid)
                }
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
                    dokumentLokasjon = "urn:hubba:bubba",
                    journalpostId = "journalpostid"
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
        init {
            person?.accept(this)
        }

        lateinit var søknader: List<Søknad>
        lateinit var aktivitetslogg: Aktivitetslogg
        override fun visitPerson(ident: String, søknader: List<Søknad>) {
            this.søknader = søknader
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

    private fun editoOprettet(søknadId: UUID, opprettetDato: LocalDateTime, ds: DataSource): Int {
        return using(sessionOf(ds)) {
            it.run(
                queryOf(
                    "UPDATE soknad_v1 SET opprettet=:opprettet WHERE uuid=:uuid",
                    mapOf("uuid" to søknadId.toString(), "opprettet" to opprettetDato)
                ).asUpdate
            )
        }
    }
}
