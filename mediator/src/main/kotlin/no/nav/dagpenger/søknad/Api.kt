package no.nav.dagpenger.søknad

import io.ktor.client.plugins.ResponseException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.document
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.søknad.utils.auth.AzureAdFactory.azure
import no.nav.dagpenger.søknad.utils.auth.TokenXFactory.tokenX
import no.nav.dagpenger.søknad.utils.serder.objectMapper
import org.slf4j.event.Level
import java.net.URI

private val logger = KotlinLogging.logger {}

internal fun Application.api(
    søknadRouteBuilder: Route.() -> Unit,
    personaliaRouteBuilder: Route.() -> Unit,
    ferdigstiltRouteBuilder: Route.() -> Unit
) {

    install(CallLogging) {
        level = Level.DEBUG
        disableDefaultColors()
        filter { call ->
            !setOf(
                "isalive",
                "isready",
                "metrics"
            ).contains(call.request.document())
        }
    }
    install(DefaultHeaders)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ResponseException -> {
                    logger.error(cause) { "Feil ved uthenting av personalia" }
                    call.respond(
                        cause.response.status,
                        HttpProblem(
                            type = URI("urn:oppslag:personalia"),
                            title = "Feil ved uthenting av personalia",
                            detail = cause.message,
                            status = cause.response.status.value,
                            instance = URI(call.request.uri)
                        )
                    )
                }
                is IllegalArgumentException -> {
                    call.respond(
                        BadRequest,
                        HttpProblem(title = "Feilet", detail = cause.message, status = 400)
                    )
                }
                is NotFoundException -> {
                    logger.info(cause) { "Kunne ikke håndtere API kall - Ikke funnet" }
                    call.respond(
                        NotFound,
                        HttpProblem(title = "Feilet", detail = cause.message, status = 404)
                    )
                }
                is BadRequestException -> {
                    logger.error(cause) { "Kunne ikke håndtere API kall - feil i request" }
                    call.respond(
                        BadRequest,
                        HttpProblem(title = "Feilet", detail = cause.message, status = 400)
                    )
                }
                is IkkeTilgangExeption -> {
                    logger.warn { "Kunne ikke håndtere API kall - Ikke tilgang" }
                    call.respond(
                        Forbidden,
                        HttpProblem(title = "Ikke tilgang", status = 403, detail = cause.message)
                    )
                }
                else -> {
                    logger.error(cause) { "Kunne ikke håndtere API kall" }
                    call.respond(
                        InternalServerError,
                        HttpProblem(title = "Feilet", detail = cause.message)
                    )
                }
            }
        }
    }
    install(ContentNegotiation) {
        jackson {
            register(ContentType.Application.Json, JacksonConverter(objectMapper))
        }
    }

    install(Authentication) {
        jwt("tokenX") {
            tokenX()
        }
        jwt("azureAd") {
            azure()
        }
    }

    routing {
        authenticate("tokenX") {
            søknadRouteBuilder()
            personaliaRouteBuilder()
        }
        authenticate("azureAd") {
            ferdigstiltRouteBuilder()
        }
    }
}

class IkkeTilgangExeption(melding: String) : RuntimeException(melding)

// As of https://tools.ietf.org/html/rfc7807
data class HttpProblem(
    val type: URI = URI.create("about:blank"),
    val title: String,
    val status: Int? = 500,
    val detail: String? = null,
    val instance: URI = URI.create("about:blank")
)
