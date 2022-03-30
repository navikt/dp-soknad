package no.nav.dagpenger.soknad.mottak

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.nySøknadBehovsløsning
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadOpprettetHendelseMottakTest {

    private val mediatorMock = mockk<SøknadMediator>().also {
        every { it.behandle(any() as SøknadOpprettetHendelse) } just Runs
    }

    private val testRapid = TestRapid().also { rapidsConnection ->
        SøknadOpprettetHendelseMottak(rapidsConnection, mediatorMock)
    }

    @Test
    fun `skal lytte på søknad opprettet melding og oversette til SøknadOpprettetHendelse`() {
        testRapid.sendTestMessage(nySøknadBehovsløsning(UUID.randomUUID().toString()))
        verify(exactly = 1) { mediatorMock.behandle(any() as SøknadOpprettetHendelse) }
    }

    @Test
    fun `Ignorer behov uten løsning`() {
    }
}
