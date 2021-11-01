package no.nav.dagpenger.quizshow.api

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.receiveText
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.routing.routing
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal fun Application.søknadApi() {

    install(DefaultHeaders)
    install(ContentNegotiation) {
        jackson {}
    }

    routing {
        route("${Configuration.basePath}/soknad") {
            get("/{id}/neste-seksjon") {
                call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { søkerOppgave }
            }
            get("/{id}/subsumsjoner") {
                call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { søkerOppgave }
            }
            put("/{id}/faktum/{faktumid}") {
                val input = call.receiveText()
                logger.info { "Fikk \n$input" }
                call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { søkerOppgave }
            }
        }
    }
}

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
