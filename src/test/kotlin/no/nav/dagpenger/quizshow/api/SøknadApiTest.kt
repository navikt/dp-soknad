package no.nav.dagpenger.quizshow.api

import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.server.testing.withTestApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class SøknadApiTest {
    private val rapid = TestRapid()
    private val mediator = Mediator(rapid)

    @BeforeEach
    fun reset() {
        rapid.reset()
    }

    @Test
    fun `tar imot seksjon-behov og pusher på websocket`() {
        withTestApplication({ søknadApi { meldingObserver -> mediator.register(meldingObserver) } }) {
            handleWebSocketConversation("/ws") { incoming, _ ->
                rapid.sendTestMessage(seksjonMessage)
                val incoming = (incoming.receive() as Frame.Text).readText()
                assertTrue(incoming.contains("fødselsnummer"))
            }
        }
    }

    val seksjonMessage =
        """
{
  "@event_name": "behov",
  "@opprettet": "2020-10-28T12:50:36.349916",
  "@id": "e685a88d-02e6-4683-b417-fd8a750162fe",
  "@behov": [
    "Ønsker dagpenger fra dato"
  ],
  "fødselsnummer": "12345678910",
  "fakta": [
    {
      "type": "GrunnleggendeFaktum",
      "navn": "Ønsker dagpenger fra dato",
      "id": "1",
      "avhengigFakta": [],
      "avhengerAvFakta": [],
      "clazz": "localdate",
      "rootId": 1,
      "indeks": 0,
      "roller": [
        "søker"
      ]
    }
  ],
  "root": {
    "rolle": "søker",
    "navn": "Datoer",
    "fakta": [
      "1"
    ]
  },
  "system_read_count": 0
}""".trimIndent()
}
