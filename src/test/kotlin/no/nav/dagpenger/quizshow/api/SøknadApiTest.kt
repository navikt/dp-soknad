package no.nav.dagpenger.quizshow.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

internal class SøknadApiTest {

    private val jackson = jacksonObjectMapper()

    val store = object : SøknadStore {

        //language=JSON
        private val søkerOppgave =
            """
  {
    "@event_name": "søker_oppgave",
    "@id": "f1387052-1132-4692-be23-803817bdf214",
    "@opprettet": "2021-11-01T14:18:34.039275",
    "søknad_uuid": "d172a832-4f52-4e1f-ab5f-8be8348d9280",
    "seksjon_navn": "gjenopptak",
    "indeks": 0,
    "identer": [
      {
        "id": "123456789",
        "type": "folkeregisterident",
        "historisk": false
      }
    ],
    "fakta": [
      {
        "navn": "Har du hatt dagpenger siste 52 uker?",
        "id": "1",
        "roller": [
          "søker"
        ],
        "type": "boolean",
        "godkjenner": []
      }
    ],
    "subsumsjoner": [
      {
        "lokalt_resultat": null,
        "navn": "Sjekk at `Har du hatt dagpenger siste 52 uker med id 1` er lik true",
        "forklaring": "saksbehandlerforklaring",
        "type": "Enkel subsumsjon",
        "fakta": [
          "1"
        ]
      }
    ]
  }
            """.trimIndent()

        override fun håndter(rettighetsavklaringMelding: ØnskerRettighetsavklaringMelding) {
        }

        override fun hent(søknadUuid: String): JsonMessage? {
            return JsonMessage(søkerOppgave, MessageProblems(søkerOppgave))
        }
    }

    @Test
    fun `Skal starte søknad`() {

        withTestApplication({ søknadApi(store) }) {
            handleRequest(HttpMethod.Post, "${Configuration.basePath}/soknad").apply {
                assertEquals(HttpStatusCode.Created, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
                val content = jackson.readTree(this.response.content)
                assertDoesNotThrow { content["uuid"].asText().also { UUID.fromString(it) } }
            }
        }
    }

    @Test
    fun `Skal hente søknad seksjoner`() {

        withTestApplication({ søknadApi(store) }) {
            handleRequest(HttpMethod.Get, "${Configuration.basePath}/soknad/187689/neste-seksjon").apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun `Skal hente søknad subsumsjoner`() {

        withTestApplication({ søknadApi(store) }) {
            handleRequest(HttpMethod.Get, "${Configuration.basePath}/soknad/187689/subsumsjoner").apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun `Skal kunne lagre faktum`() {

        withTestApplication({ søknadApi(store) }) {
            handleRequest(HttpMethod.Put, "${Configuration.basePath}/soknad/187689/faktum/1245") {
                setBody("""{"id":1, "svar": true}""")
            }

                .apply {
                    assertEquals(HttpStatusCode.OK, this.response.status())
                    assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
                }
        }
    }
}
