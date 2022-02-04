package no.nav.dagpenger.quizshow.api.personalia

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.client.features.ResponseException
import io.ktor.features.StatusPages
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.HttpProblem
import no.nav.dagpenger.quizshow.api.auth.fnr
import java.net.URI

private val logger = KotlinLogging.logger {}

internal fun Route.personalia(personOppslag: PersonOppslag, kontonummerOppslag: KontonummerOppslag) {

    this.install(StatusPages) {
        exception<ResponseException> { error ->
            logger.error(error) { "Feil ved uthenting av personalia" }
            call.respond(
                error.response.status,
                HttpProblem(
                    type = URI("urn:oppslag:personalia"),
                    title = "Feil ved uthenting av personalia",
                    detail = error.message,
                    status = error.response.status.value,
                    instance = URI(call.request.uri)
                )
            )
        }
    }

    route("${Configuration.basePath}/personalia") {
        get {
            val fnr = call.authentication.principal<JWTPrincipal>()?.fnr
                ?: throw IllegalArgumentException("Mangler pid eller sub i claim") // todo better exception
            val personalia = withContext(Dispatchers.IO) {
                val person = async { personOppslag.hentPerson(fnr) }
                val kontonummer = async { kontonummerOppslag.hentKontonummer(fnr) }
                Personalia(person.await(), kontonummer.await())
            }
            call.respond(personalia)
        }
    }
}
