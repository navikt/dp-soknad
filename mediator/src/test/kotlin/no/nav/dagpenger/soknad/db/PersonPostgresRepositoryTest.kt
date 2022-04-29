package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

internal class PersonPostgresRepositoryTest {
    @Test
    fun `Lagre og hente person uten søknader`() {
        withMigratedDb {
            PersonPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                val expectedPerson = Person("12345678910")
                it.lagre(expectedPerson)
                val person = it.hent("12345678910")

                assertNotNull(person)
                assertEquals(expectedPerson, person)
            }
        }
    }

    @Test
    fun `Hent perosn som ikke finnes`() {
        withMigratedDb {
            PersonPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                assertNull(it.hent("finnes ikke"))
            }
        }
    }

    @Test
    fun `Lagre og hente person med søknader`() {
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
            PersonPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                it.lagre(originalPerson)
                val personFraDatabase = it.hent("12345678910")
                assertNotNull(personFraDatabase)
                assertEquals(originalPerson, personFraDatabase)

                val søknaderFraDatabase = TestSøknadVisitor(personFraDatabase).søknader
                val originalSøknader = TestSøknadVisitor(originalPerson).søknader

                assertDeepEquals(originalSøknader.first(), søknaderFraDatabase.first())
                assertDeepEquals(originalSøknader.last(), søknaderFraDatabase.last())
            }
        }
    }

    private fun assertDeepEquals(expected: Søknad, result: Søknad) {
        assertTrue(expected.deepEquals(result), "Søknadene var ikke like")
    }

    private class TestSøknadVisitor(person: Person?) : PersonVisitor {
        init {
            person?.accept(this)
        }

        lateinit var søknader: List<Søknad>
        override fun visitPerson(ident: String, søknader: List<Søknad>) {
            this.søknader = søknader
        }
    }
}
