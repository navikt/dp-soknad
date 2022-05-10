package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class InnsendtSoknadRepositoryTest {

    @Test
    fun `kan lagre og hente søknad`() {
        withMigratedDb {
            val søknadId = lagRandomPersonOgSøknad()

            @Language("JSON")
            val expectedJson = """{ "id": "value" }"""
            InnsendtSoknadRepository(PostgresDataSourceBuilder.dataSource).let { db ->
                db.lagre(søknadId, expectedJson)
                val actualJson = db.hent(søknadId)
                assertJsonEquals(expectedJson, actualJson)
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
