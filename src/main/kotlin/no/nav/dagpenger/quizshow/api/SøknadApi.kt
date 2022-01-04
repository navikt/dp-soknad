package no.nav.dagpenger.quizshow.api

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.NotFoundException
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.jackson.jackson
import io.ktor.request.document
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.quizshow.api.Configuration.appName
import org.slf4j.event.Level
import soknadApi
import java.net.URI

private val logger = KotlinLogging.logger {}

internal fun Application.søknadApi(
    jwkProvider: JwkProvider,
    issuer: String,
    clientId: String,
    store: SøknadStore
) {

    // As of https://tools.ietf.org/html/rfc7807
    data class HttpProblem(
        val type: URI = URI.create("about:blank"),
        val title: String,
        val status: Int? = 500,
        val detail: String? = null,
        val instance: URI = URI.create("about:blank")
    )

    install(CallLogging) {
        level = Level.DEBUG
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
        exception<Throwable> { cause ->
            logger.error(cause) { "Kunne ikke håndtere API kall" }
            call.respond(
                InternalServerError,
                HttpProblem(title = "Feilet", detail = cause.message)
            )
        }
        exception<IllegalArgumentException> { cause ->
            call.respond(
                BadRequest,
                HttpProblem(title = "Klient feil", status = 400, detail = cause.message)
            )
        }
        exception<NotFoundException> { cause ->
            call.respond(
                NotFound,
                HttpProblem(title = "Ikke funnet", status = 404, detail = cause.message)
            )
        }
    }
    install(ContentNegotiation) {
        jackson {}
    }

    install(Authentication) {
        jwt {
            verifier(jwkProvider, issuer) {
                withAudience(clientId)
            }
            realm = appName
            validate { credentials ->
                requireNotNull(credentials.payload.claims["pid"]) {
                    "Token må inneholde fødselsnummer for personen"
                }

                JWTPrincipal(credentials.payload)
            }
        }
    }

    routing {
        authenticate {
            soknadApi(store)
        }
    }
}
