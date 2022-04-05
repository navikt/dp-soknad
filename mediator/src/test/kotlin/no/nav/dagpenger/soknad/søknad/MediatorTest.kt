package no.nav.dagpenger.soknad.søknad

import no.nav.dagpenger.soknad.db.Postgres
import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

class MediatorTest {
    private val testRapid = TestRapid()

    @AfterEach
    fun reset() {
        testRapid.reset()
    }

    @Test
    fun `lese svar fra kafka`() {

        Postgres.withMigratedDb {
            val mediator = Mediator(testRapid, PostgresPersistence(PostgresDataSourceBuilder.dataSource))
            testRapid.reset()
            val søknadUuid = UUID.randomUUID()
            testRapid.sendTestMessage(nySøknad(søknadUuid))
            mediator.hentFakta(søknadUuid).also {
                assertDoesNotThrow {
                    assertEquals("fakta", it.fakta().asText())
                    assertEquals("12345678910", it.eier())
                    assertEquals(søknadUuid, it.søknadUUID())
                }
            }
        }
    }
    //language=JSON
    private fun nySøknad(søknadUuid: UUID) = """{
          "@event_name": "NySøknad",
          "fakta": "fakta",
          "fødselsnummer": "12345678910",
          "søknad_uuid": "$søknadUuid",
          "@opprettet": "2022-01-13T09:40:19.158310"
        }
    """.trimIndent()
}
