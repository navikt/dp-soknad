package no.nav.dagpenger.quizshow.api.personalia

import io.ktor.application.call
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.dagpenger.quizshow.api.Configuration

internal fun Route.personalia() {
    route("${Configuration.basePath}/personalia") {
        get {
            call.respondText("OK")
        }
    }
}
