package no.nav.dagpenger.quizshow.api

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediatorTest {
    private val testRapid = TestRapid()
    private val mediator = Mediator(testRapid)

    @Test
    fun `publiserer ny-søknadsmelding på kafka`() {
        val fnr = "12345678910"
        mediator.håndter(ØnskerRettighetsavklaringMelding(fnr))
        testRapid.inspektør.message(0).also {
            assertEquals(fnr, it["fødselsnummer"].asText())
            assertTrue(it.has("avklaringsId"))
            assertTrue(it.has("@event_name"))
            assertEquals("ønsker_rettighetsavklaring", it["@event_name"].asText())
        }
    }

    @Test
    fun `Skal lagre meldinger basert på fnr`() = runBlocking {
        testRapid.sendTestMessage(søkerOppgave())
        assertNotNull(mediator.hent("35cfb1bd-4dc9-4057-b51d-1b5acff75248"))
    }

    //language=JSON
    private fun søkerOppgave() =
        """
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
