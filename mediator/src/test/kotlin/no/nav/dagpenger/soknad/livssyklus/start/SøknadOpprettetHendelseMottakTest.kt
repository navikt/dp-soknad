package no.nav.dagpenger.soknad.livssyklus.start

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

internal class SøknadOpprettetHendelseMottakTest {
    private val mediatorMock = mockk<SøknadMediator>().also {
        every { it.prosessversjon(any(), any()) } returns Prosessversjon(prosessnavn, prosessversjon)
        every { it.behandle(any() as SøknadOpprettetHendelse) } just Runs
    }
    private val testRapid = TestRapid().also { rapidsConnection ->
        SøknadOpprettetHendelseMottak(rapidsConnection, mediatorMock)
    }

    @Test
    fun `skal lytte på søknad opprettet melding og oversette til SøknadOpprettetHendelse`() {
        testRapid.sendTestMessage(nySøknadBehovsløsning(UUID.randomUUID().toString()))
        val hendelse = slot<SøknadOpprettetHendelse>()
        verify(exactly = 1) { mediatorMock.behandle(capture(hendelse)) }

        with(hendelse.captured) {
            assertEquals(prosessnavn, prosessversjon().prosessnavn.id)
            assertEquals(prosessversjon, prosessversjon().versjon)
        }
    }

    @Test
    fun `Ignorer behov uten løsning`() {
        testRapid.sendTestMessage(nySøknadBehovUtenLøsning(UUID.randomUUID().toString()))
        verify(exactly = 0) { mediatorMock.behandle(any() as SøknadOpprettetHendelse) }
    }

    private companion object {
        private val prosessnavn = "Dagpenger"
        private val prosessversjon = Random.nextInt(0, Int.MAX_VALUE)

        fun nySøknadBehovsløsning(søknadUuid: String) = // language=JSON
            """
            {
              "@event_name": "behov",
              "@behovId": "99a145c8-b8c8-4317-9963-d8e6dd0f5859",
              "@behov": [
                "NySøknad"
              ],
              "søknad_uuid": "$søknadUuid",
              "ident": "08896699289",
              "NySøknad": {
                "prosessnavn": "Dagpenger"
              },
              "prosessnavn": "Dagpenger",
              "@id": "a6f500e3-5890-4f5d-9ff9-b7886b2c36b2",
              "@opprettet": "2022-11-10T19:51:20.702227520",
              "system_read_count": 2,
              "system_participating_services": [
                                           {
                                           "id": "b8f8d3ea-0a96-414d-92c7-9604e7c64a84",
                                           "time": "2022-11-10T19:51:20.511832342",
                                           "service": "dp-soknad",
                                           "instance": "dp-soknad-8b5987f4c-6vr6l",
                                           "image": "ghcr.io/navikt/dp-soknad:52d0d2d853bc5845b16064c3f409bfa67042d789"
                                           },
                                           {
                                           "id": "b8f8d3ea-0a96-414d-92c7-9604e7c64a84",
                                           "time": "2022-11-10T19:51:20.515856976",
                                           "service": "dp-quiz-mediator",
                                           "instance": "dp-quiz-mediator-6dcb799cbb-5n4f6",
                                           "image": "ghcr.io/navikt/dp-quiz/dp-quiz-mediator:209df85b86458fbba939cb750df1156610f38660"
                                           },
                                           {
                                           "id": "a6f500e3-5890-4f5d-9ff9-b7886b2c36b2",
                                           "time": "2022-11-10T19:51:20.702227520",
                                           "service": "dp-quiz-mediator",
                                           "instance": "dp-quiz-mediator-6dcb799cbb-5n4f6",
                                           "image": "ghcr.io/navikt/dp-quiz/dp-quiz-mediator:209df85b86458fbba939cb750df1156610f38660"
                                           },
                                           {
                                           "id": "a6f500e3-5890-4f5d-9ff9-b7886b2c36b2",
                                           "time": "2022-11-10T19:51:20.707936751",
                                           "service": "dp-soknad",
                                           "instance": "dp-soknad-8b5987f4c-277df",
                                           "image": "ghcr.io/navikt/dp-soknad:52d0d2d853bc5845b16064c3f409bfa67042d789"
                                           }
                                           ],
              "@løsning": {
                "NySøknad": {
                  "prosessversjon": {
                    "prosessnavn": "$prosessnavn",
                    "versjon": $prosessversjon 
                  }
                }
              },
              "@forårsaket_av": {
                "id": "b8f8d3ea-0a96-414d-92c7-9604e7c64a84",
                "opprettet": "2022-11-10T19:51:20.511832342",
                "event_name": "behov",
                "behov": [
                  "NySøknad"
                ]
              }
            } 
            """.trimMargin()

        fun nySøknadBehovUtenLøsning(søknadUuid: String) = // language=JSON
            """
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
            }
            """.trimMargin()
    }
}
