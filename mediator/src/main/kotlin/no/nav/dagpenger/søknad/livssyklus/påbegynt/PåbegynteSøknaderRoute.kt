package no.nav.dagpenger.søknad.livssyklus

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.utils.auth.ident

internal fun Route.påbegyntSøknadRoute(søknadMediator: SøknadMediator) {
    get("/paabegynt") {
        val påbegyntSøknad = søknadMediator.hentPåbegyntSøknad(call.ident())

        if (påbegyntSøknad != null) {
            call.respond(HttpStatusCode.OK, påbegyntSøknad)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
