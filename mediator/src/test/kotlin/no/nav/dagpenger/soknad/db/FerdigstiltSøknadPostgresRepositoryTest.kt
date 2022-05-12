package no.nav.dagpenger.soknad.db

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PGobject
import java.util.UUID

internal class FerdigstiltSøknadPostgresRepositoryTest {
    @Language("JSON")
    private val dummyTekst = """{ "id": "value" }"""
    private val dummyFakta = """{ "fakta1": "value1" }"""

    @Test
    fun `kan lagre og hente søknads tekst`() {
        withMigratedDb {
            val søknadId = lagRandomPersonOgSøknad()

            FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).let { db ->
                db.lagreSøknadsTekst(søknadId, dummyTekst)
                val actualJson = db.hentTekst(søknadId)
                assertJsonEquals(dummyTekst, actualJson)
            }
        }
    }

    @Test
    fun `kaster expetion hvis søknadstekst ikke finnes`() {
        assertThrows<NotFoundException> {
            withMigratedDb {
                FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).hentTekst(UUID.randomUUID())
            }
        }
    }

    @Test
    fun `kaster exeption når søknaduuid allerede finnes`() {
        assertThrows<IllegalArgumentException> {
            withMigratedDb {
                val søknadUUID = lagRandomPersonOgSøknad()
                FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).lagreSøknadsTekst(
                    søknadUUID,
                    dummyTekst
                )
                FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).lagreSøknadsTekst(
                    søknadUUID,
                    dummyTekst
                )
            }
        }
    }

    @Test
    fun `kan hente søknadsfakta`() {
        withMigratedDb {
            val søknadId = lagreFakta(dummyFakta)
            FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).let { db ->
                val actualJson = db.hentFakta(søknadId)
                assertJsonEquals(dummyFakta, actualJson)
            }
        }
    }

    @Test
    fun `kaster exception dersom søknadsfakta ikke eksister`() {
        withMigratedDb {
            assertThrows<NotFoundException> {
                FerdigstiltSøknadPostgresRepository(PostgresDataSourceBuilder.dataSource).hentFakta(UUID.randomUUID())
            }
        }
    }

    private fun lagreFakta(fakta: String): UUID {
        val søknadId = UUID.randomUUID()
        using(sessionOf(PostgresDataSourceBuilder.dataSource)) { session ->
            session.run(
                queryOf(
                    """INSERT INTO soknad(uuid, eier, soknad_data)
                                    VALUES (:uuid, :eier, :soknad_data) """,
                    mapOf(
                        "uuid" to søknadId.toString(),
                        "eier" to "eier",
                        "soknad_data" to PGobject().also {
                            it.type = "jsonb"
                            it.value = fakta
                        }
                    )
                ).asUpdate
            )
        }
        return søknadId
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
