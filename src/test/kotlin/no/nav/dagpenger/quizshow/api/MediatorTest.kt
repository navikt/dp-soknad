package no.nav.dagpenger.quizshow.api

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediatorTest {
    private val testRapid = TestRapid()
    private val mediator = Mediator(testRapid)

    @Test
    fun `publiserer ny-faktamelding på kafka`() {
        val fnr = "12345678910"
        mediator.håndter(NySøknadMelding(fnr))
        testRapid.inspektør.message(0).also {
            assertTrue(it.has("@id"))
            assertTrue(it.has("@event_name"))
            assertTrue(it.has("søknad_uuid"))
            assertEquals(fnr, it["fødselsnummer"].asText())
            assertEquals("NySøknad", it["@event_name"].asText())
        }
    }

    @Test
    fun `lese svar fra kafka`() {
        val testRapid = TestRapid()
        val persistence = mockk<Persistence>().also {
            justRun {
                it.lagre(any(), any())
            }
        }

        val mediator = Mediator(testRapid, persistence)

        //language=JSON
        val message = """{
          "@event_name": "NySøknad",
          "fakta": "{}",
          "fødselsnummer": "12345678910",
          "søknad_uuid": "123",
          "@opprettet": "2022-01-13T09:40:19.158310"
        }
        """.trimIndent()

        testRapid.sendTestMessage(
            message
        )

        verify(exactly = 1) {
            persistence.lagre("123", any())
        }
    }
}
