package no.nav.dagpenger.soknad.livssyklus.start

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
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
        testRapid.sendTestMessage(nySøknadBehovUtenLøsning(UUID.randomUUID().toString()))
        verify(exactly = 0) { mediatorMock.behandle(any() as SøknadOpprettetHendelse) }
    }

    companion object {

        // language=JSON
        fun nySøknadBehovsløsning(søknadUuid: String) = """
        {
          "@event_name": "behov",
          "@behovId": "84a03b5b-7f5c-4153-b4dd-57df041aa30d",
          "@behov": [
            "NySøknad"
          ],
          "ident": "12345678912",
          "søknad_uuid": "$søknadUuid",
          "NySøknad": {},
          "@id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
          "@opprettet": "2022-03-30T12:19:08.418821",
          "system_read_count": 0,
          "system_participating_services": [
            {
              "id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
              "time": "2022-03-30T12:19:08.418821"
            }
          ],
          "@løsning": {"NySøknad": "$søknadUuid"}
        }""".trimMargin()

        // language=JSON
        fun nySøknadBehovUtenLøsning(søknadUuid: String) = """
        {
          "@event_name": "behov",
          "@behovId": "84a03b5b-7f5c-4153-b4dd-57df041aa30d",
          "@behov": [
            "NySøknad"
          ],
          "ident": "12345678912",
          "søknad_uuid": "$søknadUuid",
          "NySøknad": {},
          "@id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
          "@opprettet": "2022-03-30T12:19:08.418821",
          "system_read_count": 0, 
          "system_participating_services": [
            {
              "id": "cf3f3303-121d-4d6d-be0b-5b2808679a79",
              "time": "2022-03-30T12:19:08.418821"
            }
          ]
        }""".trimMargin()
    }
}
