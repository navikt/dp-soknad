package no.nav.dagpenger.quizshow.api.personalia

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.client.features.ResponseException
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.HttpProblem
import no.nav.dagpenger.quizshow.api.auth.fnr
import java.net.URI

internal fun Route.personalia(personOppslag: PersonOppslag, kontonummerOppslag: KontonummerOppslag) {
    route("${Configuration.basePath}/personalia") {
        get {
            val fnr = call.authentication.principal<JWTPrincipal>()?.fnr
                ?: throw IllegalArgumentException("Mangler pid eller sub i claim") // todo better exception
            call.respond(personOppslag.hentPerson(fnr))
        }
    }
    route("${Configuration.basePath}/personalia/kontonummer") {
        get {
            try {
                val fnr = call.authentication.principal<JWTPrincipal>()?.fnr
                    ?: throw IllegalArgumentException("Mangler pid eller sub i claim")
                call.respond(kontonummerOppslag.hentKontonummer(fnr))
            } catch (e: ResponseException) {
                call.respond(
                    e.response.status,
                    HttpProblem(
                        type = URI("urn:oppslag:kontonummer"),
                        title = "Feil ved uthenting av kontonummer",
                        detail = e.message,
                        status = e.response.status.value,
                        instance = URI(call.request.uri)
                    )
                )
            }
        }
    }
}
