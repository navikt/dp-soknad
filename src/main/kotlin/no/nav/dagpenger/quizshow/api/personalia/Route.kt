package no.nav.dagpenger.quizshow.api.personalia

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.auth.fnr

internal fun Route.personalia(personOppslag: PersonOppslag) {
    route("${Configuration.basePath}/personalia") {
        get {
            val fnr = call.authentication.principal<JWTPrincipal>()?.fnr
                ?: throw IllegalArgumentException() // todo better exception
            call.respond(personOppslag.hentPerson(fnr))
        }
    }
}
