package no.nav.dagpenger.soknad.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.dagpenger.soknad.SøknadObserver.SøknadSlettetEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class SøknadTilstandObserverTest {

    val testRapid = TestRapid()
    val søknadUuid = UUID.randomUUID()
    val personIdent = "12345678901"

    @Test
    fun `Skal sende slettet søknad event på kafka`() {
        val søknadTilstandObserver = SøknadTilstandObserver(testRapid)
        søknadTilstandObserver.søknadSlettet(SøknadSlettetEvent(søknadUuid, personIdent))

        assertEquals(1, testRapid.inspektør.size)

        with(testRapid.inspektør) {
            assertEquals("søknad_slettet", field(0, "@event_name").asText())
            assertEquals(personIdent, key(0))
            assertNotNull(field(0, "@id").also { UUID.fromString(it.asText()) })
            assertNotNull(field(0, "@opprettet").also { LocalDateTime.parse(it.asText()) })
            assertNotNull(field(0, "søknad_uuid").also { UUID.fromString(it.asText()) })
            assertEquals(personIdent, field(0, "ident").asText())
        }
    }
}
