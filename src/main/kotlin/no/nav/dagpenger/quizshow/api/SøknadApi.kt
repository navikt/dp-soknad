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
