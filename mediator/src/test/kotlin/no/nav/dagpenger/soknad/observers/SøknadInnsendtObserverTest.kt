package no.nav.dagpenger.soknad.observers

import junit.framework.TestCase.assertEquals
import no.nav.dagpenger.soknad.SøknadObserver
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull

class SøknadInnsendtObserverTest {

    val testRapid = TestRapid()
    val søknadId = UUID.randomUUID()
    val ident = "12345678901"

    @Test
    fun `Sender ut melding om søknad innsendt på kafka`() {
        val søknadInnsendtObserver = SøknadInnsendtObserver(testRapid)
        val innsendt = ZonedDateTime.now(ZoneId.of("Europe/Oslo"))

        søknadInnsendtObserver.søknadInnsendt(SøknadObserver.SøknadInnsendtEvent(søknadId, innsendt, "", ident))

        assertEquals(1, testRapid.inspektør.size)

        with(testRapid.inspektør) {
            assertEquals("søknad_innsendt", field(0, "@event_name").asText())
            assertEquals(søknadId, field(0, "søknadId").asUUID())
            assertNotNull(field(0, "søknadstidspunkt"))
            assertNotNull(field(0, "søknadData"))
            assertEquals(ident, field(0, "ident").asText())
        }
    }
}
