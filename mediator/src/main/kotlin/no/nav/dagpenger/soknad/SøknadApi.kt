package no.nav.dagpenger.soknad

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.NotFoundException
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.jackson.JacksonConverter
import io.ktor.request.document
import io.ktor.response.respond
import io.ktor.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Configuration.appName
import no.nav.dagpenger.soknad.auth.validator
import no.nav.dagpenger.soknad.personalia.KontonummerOppslag
import no.nav.dagpenger.soknad.personalia.PersonOppslag
import no.nav.dagpenger.soknad.personalia.personalia
import no.nav.dagpenger.soknad.serder.objectMapper
import no.nav.dagpenger.soknad.søknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.søknad.SøknadStore
import no.nav.dagpenger.soknad.søknad.api
import org.slf4j.event.Level

private val logger = KotlinLogging.logger {}

internal fun Application.søknadApi(
    jwkProvider: JwkProvider,
    issuer: String,
    clientId: String,
    store: SøknadStore,
    personOppslag: PersonOppslag,
    kontonummerOppslag: KontonummerOppslag,
    søknadMediator: SøknadMediator
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
        exception<Throwable> { cause ->
            logger.error(cause) { "Kunne ikke håndtere API kall" }
            call.respond(
                InternalServerError,
                HttpProblem(title = "Feilet", detail = cause.message)
            )
        }
        exception<IllegalArgumentException> { cause ->
            logger.warn(cause) { "Kunne ikke håndtere API kall - Klient feil" }
            call.respond(
                BadRequest,
                HttpProblem(title = "Klient feil", status = 400, detail = cause.message)
            )
        }
        exception<NotFoundException> { cause ->
            logger.info(cause) { "Kunne ikke håndtere API kall - Ikke funnet" }
            call.respond(
                NotFound,
                HttpProblem(title = "Ikke funnet", status = 404, detail = cause.message)
            )
        }
        exception<IkkeTilgangExeption> { cause ->
            logger.warn { "Kunne ikke håndtere API kall - Ikke tilgang" }
            call.respond(
                Forbidden,
                HttpProblem(title = "Ikke tilgang", status = 403, detail = cause.message)
            )
        }
    }
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }

    install(Authentication) {
        jwt {
            verifier(jwkProvider, issuer) {
                withAudience(clientId)
            }
            realm = appName
            validate { credentials ->
                validator(credentials)
            }
        }
    }

    routing {
        authenticate {
            api(logger, store, søknadMediator)
            personalia(personOppslag, kontonummerOppslag)
        }
    }
}
