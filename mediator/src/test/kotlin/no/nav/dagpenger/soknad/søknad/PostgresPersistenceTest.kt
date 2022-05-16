package no.nav.dagpenger.soknad.søknad

import com.fasterxml.jackson.module.kotlin.contains
import io.ktor.server.plugins.NotFoundException
import no.nav.dagpenger.soknad.db.Postgres
import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.soknad.serder.objectMapper
import no.nav.dagpenger.soknad.søknad.Søknad.Keys.FØDSELSNUMMER
import no.nav.dagpenger.soknad.søknad.Søknad.Keys.SEKSJONER
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
            assertEquals(søknad.asFrontendformat(), rehydrertSøknad.asFrontendformat())
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
                        seksjoner = "oppdatert første gang"
                    )
                )
            )
            postgresPersistence.lagre(
                PostgresPersistence.PersistentSøknad(
                    søknad(
                        søknadUuid,
                        seksjoner = "oppdatert andre gang"
                    )
                )
            )
            val rehydrertSøknad = postgresPersistence.hent(søknadUuid)
            assertEquals(søknadUuid, rehydrertSøknad.søknadUUID())
            assertEquals("12345678910", rehydrertSøknad.eier())
            assertEquals("oppdatert andre gang", rehydrertSøknad.asFrontendformat()[SEKSJONER].asText())
        }
    }

    @Test
    fun `Henter en søknad som ikke finnes`() {
        Postgres.withMigratedDb {
            val postgresPersistence = PostgresPersistence(dataSource)
            assertThrows<NotFoundException> { postgresPersistence.hent(UUID.randomUUID()) }
        }
    }

    @Test
    fun `Fødselsnummer skal ikke komme med som en del av frontendformatet, men skal fortsatt være en del av søknaden`() {
        val søknadJson = søknad(UUID.randomUUID())
        val søknad = PostgresPersistence.PersistentSøknad(søknadJson)

        val frontendformat = søknad.asFrontendformat()
        assertFalse(frontendformat.contains(FØDSELSNUMMER))
        assertNotNull(søknad.eier())
    }

    private fun søknad(søknadUuid: UUID, seksjoner: String = "seksjoner") = objectMapper.readTree(
        """{
  "@event_name": "søker_oppgave",
  "fødselsnummer": "12345678910",
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
