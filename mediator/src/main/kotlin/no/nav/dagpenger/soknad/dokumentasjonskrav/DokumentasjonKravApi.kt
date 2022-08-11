package no.nav.dagpenger.soknad.dokumentasjonskrav

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.søknadUuid

val logger = KotlinLogging.logger { }

internal fun Route.dokumentasjonkravRoute() {
    get("/{søknad_uuid}/dokumentasjonkrav") {
        val søknadUuid = søknadUuid()
        withLoggingContext("søknadid" to søknadUuid.toString()) {
        }
        call.respondText(
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK,
        ) {
            """
               {
    "soknad_uuid": "$søknadUuid",
    "krav": [
        {
            "id": "5678",
            "beskrivendeId": "arbeidsforhold.1",
            "filer": [
                {
                    "filnavn": "hei på du1.jpg", 
                    "urn": "urn:dokumen1"
                }, 
                {
                    "filnavn": "hei på du2.jpg", 
                    "urn": "urn:dokumen2"
                }, 
                {
                    "filnavn": "hei på du3.jpg", 
                    "urn": "urn:dokumen3"
                }
            ],
            "gyldigeValg": ["Laste opp nå", "Sende senere", "Noen andre sender dokumentet", "Har sendt tidligere", "Sender ikke"],
            "svar": "Laste opp nå"
        },
        {
            "id": "56789",
            "beskrivendeId": "arbeidsforhold.2",
            "filer": [
                {
                    "filnavn": "hei på du1.jpg", 
                    "urn": "urn:dokumen1"
                }, 
                {
                    "filnavn": "hei på du2.jpg", 
                    "urn": "urn:dokumen2"
                }, 
                {
                    "filnavn": "hei på du3.jpg", 
                    "urn": "urn:dokumen3"
                }
            ],
            "gyldigeValg": ["Laste opp nå", "Sende senere", "Noen andre sender dokumentet", "Har sendt tidligere", "Sender ikke"],
            "svar": "Laste opp nå"
        }
    ]
}  
            """
        }
    }

    put("/{søknad_uuid}/dokumentasjonkrav/{kravId}") {
        val søknadUuid = søknadUuid()
        withLoggingContext("søknadid" to søknadUuid.toString()) {
            call.receive<JsonNode>().let { logger.info { "Received: $it" } }
            call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { """{"status": "ok"}""" }
        }
    }
}
