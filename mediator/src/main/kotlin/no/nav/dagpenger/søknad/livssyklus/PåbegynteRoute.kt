package no.nav.dagpenger.søknad.livssyklus

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.auth.ident

internal fun Route.påbegynteRoute(søknadMediator: SøknadMediator) {
    get("/paabegynte") {
        call.respond(HttpStatusCode.OK, søknadMediator.hentPåbegynte(call.ident()))
    }
}
