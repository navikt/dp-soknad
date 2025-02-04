package no.nav.dagpenger.soknad.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import junit.framework.TestCase.assertEquals
import no.nav.dagpenger.soknad.SøknadObserver
import no.nav.dagpenger.soknad.utils.asUUID
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
        assertEquals(ident, testRapid.inspektør.key(0).toString())

        with(testRapid.inspektør) {
            assertEquals("søknad_innsendt_varsel", field(0, "@event_name").asText())
            assertEquals(søknadId, field(0, "søknadId").asUUID())
            assertNotNull(field(0, "søknadstidspunkt"))
            assertNotNull(field(0, "søknadData"))
            assertEquals(ident, field(0, "ident").asText())
        }
    }
}
