package no.nav.dagpenger.quizshow.api

import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediatorTest {
    private val rapid = TestRapid()
    private val mediator = Mediator(rapid)

    @Test
    fun `publiserer ny-søknadsmelding på kafka`() {
        val fnr = "12345678910"
        mediator.nySøknad(fnr)
        rapid.inspektør.message(0).also {
            assertEquals(fnr, it["fødselsnummer"].asText())
            assertTrue(it.has("avklaringsId"))
            assertTrue(it.has("@event_name"))
            assertEquals("ønsker_rettighetsavklaring", it["@event_name"].asText())
        }
    }
}