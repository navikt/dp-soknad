package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class InnsendtSoknadRepositoryTest {
    @Language("JSON")
    private val dummySøknad = """{ "id": "value" }"""

    @Test
    fun `kan lagre og hente søknad`() {
        withMigratedDb {
            val søknadId = lagRandomPersonOgSøknad()

            InnsendtSoknadRepository(PostgresDataSourceBuilder.dataSource).let { db ->
                db.lagre(søknadId, dummySøknad)
                val actualJson = db.hent(søknadId)
                assertJsonEquals(dummySøknad, actualJson)
            }
        }
    }

    @Test
    fun `kaster expetion hvis søknad ikke finnes`() {
        assertThrows<SoknadNotFoundException> {
            withMigratedDb {
                InnsendtSoknadRepository(PostgresDataSourceBuilder.dataSource).hent(UUID.randomUUID())
            }
        }
    }

    @Test
    fun `kaster exeption når søknaduuid allerede finnes`() {
        assertThrows<IllegalArgumentException> {
            withMigratedDb {
                val søknadUUID = lagRandomPersonOgSøknad()
                InnsendtSoknadRepository(PostgresDataSourceBuilder.dataSource).lagre(søknadUUID, dummySøknad)
                InnsendtSoknadRepository(PostgresDataSourceBuilder.dataSource).lagre(søknadUUID, dummySøknad)
            }
        }
    }

    private fun lagRandomPersonOgSøknad(): UUID {
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
        LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource).lagre(originalPerson)
        return søknadId
    }

    private fun assertJsonEquals(expected: String, actual: String) {
        fun String.removeWhitespace(): String = this.replace("\\s".toRegex(), "")
        assertEquals(expected.removeWhitespace(), actual.removeWhitespace())
    }
}
