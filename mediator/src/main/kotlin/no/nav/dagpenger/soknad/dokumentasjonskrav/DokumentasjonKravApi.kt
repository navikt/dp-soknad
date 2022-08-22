package no.nav.dagpenger.soknad.dokumentasjonskrav

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.ident

val logger = KotlinLogging.logger { }

internal fun Route.dokumentasjonkravRoute(søknadRepository: SøknadRepository) {
    get("/{søknad_uuid}/dokumentasjonkrav") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        withLoggingContext("søknadid" to søknadUuid.toString()) {
            val dokumentkrav = søknadRepository.hentDokumentkravFor(søknadUuid, ident)
            val aktivedokumentkrav = dokumentkrav.aktiveDokumentKrav().toApiKrav()
            call.respond(aktivedokumentkrav)
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
fun Set<Krav>.toApiKrav(): List<ApiDokumentKrav> = map {
    ApiDokumentKrav(
        id = it.id,
        beskrivendeId = it.beskrivendeId,
        filer = emptyList(),
    )
}

data class ApiDokumentKrav(
    val id: String,
    val beskrivendeId: String,
    val filer: List<String>,
    val gyldigeValg: Set<String> = setOf("Laste opp nå", "Sende senere", "Noen andre sender dokumentet", "Har sendt tidligere", "Sender ikke"),
    val svar: String? = null
)
