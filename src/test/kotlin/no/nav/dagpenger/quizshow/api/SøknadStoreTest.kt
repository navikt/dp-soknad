package no.nav.dagpenger.quizshow.api

import junit.framework.Assert.assertNotNull
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.Test

internal class SøknadStoreTest {

    private val testRapid = TestRapid()

    @Test
    fun `Skal lagre meldinger basert på fnr`() = runBlocking {
        val store = SøknadStore(testRapid)
        testRapid.sendTestMessage(søkerOppgave())
        assertNotNull(store.hent("12020052345"))

    }
    //language=JSON
    private fun søkerOppgave()  =
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