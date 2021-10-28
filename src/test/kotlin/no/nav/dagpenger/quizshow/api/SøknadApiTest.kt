package no.nav.dagpenger.quizshow.api

import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.server.testing.withTestApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class SøknadApiTest {
    private val rapid = TestRapid()
    private val mediator = Mediator(rapid)

    @BeforeEach
    fun reset() {
        rapid.reset()
    }

    @Test
    @Disabled
    fun `tar imot seksjon-behov og pusher på websocket`() {
        withTestApplication({ søknadApi { meldingObserver -> mediator.register(meldingObserver) } }) {
            handleWebSocketConversation("/arbeid/dagpenger/quizshow/api/ws") { incoming, _ ->
                rapid.sendTestMessage(søkerJson)
                val resultat = (incoming.receive() as Frame.Text).readText()
                assertTrue(resultat.contains("søker_oppgave"))
            }
        }
    }

    //language=JSON
    val søkerJson = """
        {
          "@event_name": "søker_oppgave",
          "@id": "900b273c-d1e2-4037-b2ae-0ff252c61896",
          "@opprettet": "2021-10-27T09:49:05.081590",
          "søknad_uuid": "35cfb1bd-4dc9-4057-b51d-1b5acff75248",
          "seksjon_navn": "søker",
          "identer": [
            {
              "id": "12020052345",
              "type": "folkeregisterident",
              "historisk": false
            },
            {
              "id": "aktørId",
              "type": "aktørid",
              "historisk": false
            }
          ],
          "fakta": [
            {
              "navn": "Oversatt tekst",
              "id": "1",
              "roller": [
                "søker"
              ],
              "type": "boolean",
              "godkjenner": []
            },
            {
              "navn": "Oversatt tekst",
              "id": "3",
              "roller": [
                "søker"
              ],
              "type": "boolean",
              "godkjenner": []
            }
          ]
        }
    """.trimIndent()
}
