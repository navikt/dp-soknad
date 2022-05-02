package no.nav.dagpenger.soknad.søknad

import io.ktor.server.plugins.NotFoundException
import no.nav.dagpenger.soknad.db.Postgres
import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.soknad.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class PostgresPersistenceTest {

    @Test
    fun `Lagre søknad og hente`() {
        Postgres.withMigratedDb {
            val postgresPersistence = PostgresPersistence(dataSource)
            val søknadUuid = UUID.randomUUID()
            val søknad = PostgresPersistence.PersistentSøknad(søknad(søknadUuid))
            postgresPersistence.lagre(søknad)

            val rehydrertSøknad = postgresPersistence.hent(søknadUuid)
            assertEquals(søknad.søknadUUID(), rehydrertSøknad.søknadUUID())
            assertEquals(søknad.eier(), rehydrertSøknad.eier())
            assertEquals(søknad.fakta(), rehydrertSøknad.fakta())
        }
    }

    @Test
    fun `Lagre samme søknad id flere ganger appendes på raden, men siste versjon av søknad hentes`() {
        val søknadUuid = UUID.randomUUID()
        Postgres.withMigratedDb {
            val postgresPersistence = PostgresPersistence(dataSource)
            postgresPersistence.lagre(PostgresPersistence.PersistentSøknad(søknad(søknadUuid)))
            postgresPersistence.lagre(
                PostgresPersistence.PersistentSøknad(
                    søknad(
                        søknadUuid,
                        fakta = "oppdatert første gang"
                    )
                )
            )
            postgresPersistence.lagre(
                PostgresPersistence.PersistentSøknad(
                    søknad(
                        søknadUuid,
                        fakta = "oppdatert andre gang"
                    )
                )
            )
            val rehydrertSøknad = postgresPersistence.hent(søknadUuid)
            assertEquals(søknadUuid, rehydrertSøknad.søknadUUID())
            assertEquals("12345678910", rehydrertSøknad.eier())
            assertEquals("oppdatert andre gang", rehydrertSøknad.fakta().asText())
        }
    }

    @Test
    fun `Henter en søknad som ikke finnes`() {
        Postgres.withMigratedDb {
            val postgresPersistence = PostgresPersistence(dataSource)
            assertThrows<NotFoundException> { postgresPersistence.hent(UUID.randomUUID()) }
        }
    }

    private fun søknad(søknadUuid: UUID, fakta: String = "fakta") = objectMapper.readTree(
        """{
          "@event_name": "NySøknad",
          "fakta": "$fakta",
          "fødselsnummer": "12345678910",
          "søknad_uuid": "$søknadUuid",
          "@opprettet": "2022-01-13T09:40:19.158310"
        }"""
    )
}
