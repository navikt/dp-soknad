package no.nav.dagpenger.søknad.observers

import no.nav.dagpenger.søknad.PersonObserver.SøknadSlettetEvent
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class SøknadSlettetObserverTest {

    val testRapid = TestRapid()
    val søknadUuid = UUID.randomUUID()
    val personIdent = "12345678901"

    @Test
    fun `Skal sende slettet søknad event på kafka`() {
        val søknadSlettetObserver = SøknadSlettetObserver(testRapid)
        søknadSlettetObserver.søknadSlettet(SøknadSlettetEvent(søknadUuid, personIdent))

        assertEquals(1, testRapid.inspektør.size)

        with(testRapid.inspektør) {
            assertEquals("søknad_slettet", field(0, "@event_name").asText())
            assertEquals(personIdent, key(0))
            assertNotNull(field(0, "@id").also { UUID.fromString(it.asText()) })
            assertNotNull(field(0, "@opprettet").also { LocalDateTime.parse(it.asText()) })
            assertNotNull(field(0, "søknad_uuid").also { UUID.fromString(it.asText()) })
        }
    }
}
