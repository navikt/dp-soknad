package no.nav.dagpenger.quizshow.api

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
    }
}
