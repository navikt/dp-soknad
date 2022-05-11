package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class FerdigstiltSøknadRepositoryTest {
    @Language("JSON")
    private val dummySøknad = """{ "id": "value" }"""

    @Test
    fun `kan lagre og hente søknads tekst`() {
        withMigratedDb {
            val søknadId = lagRandomPersonOgSøknad()

            FerdigstiltSøknadRepository(PostgresDataSourceBuilder.dataSource).let { db ->
                db.lagreSøknadsTekst(søknadId, dummySøknad)
                val actualJson = db.hentTekst(søknadId)
                assertJsonEquals(dummySøknad, actualJson)
            }
        }
    }

    @Test
    fun `kaster expetion hvis søknadstekst ikke finnes`() {
        assertThrows<SoknadNotFoundException> {
            withMigratedDb {
                FerdigstiltSøknadRepository(PostgresDataSourceBuilder.dataSource).hentTekst(UUID.randomUUID())
            }
        }
    }

    @Test
    fun `kaster exeption når søknaduuid allerede finnes`() {
        assertThrows<IllegalArgumentException> {
            withMigratedDb {
                val søknadUUID = lagRandomPersonOgSøknad()
                FerdigstiltSøknadRepository(PostgresDataSourceBuilder.dataSource).lagreSøknadsTekst(søknadUUID, dummySøknad)
                FerdigstiltSøknadRepository(PostgresDataSourceBuilder.dataSource).lagreSøknadsTekst(søknadUUID, dummySøknad)
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
