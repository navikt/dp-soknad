package no.nav.dagpenger.søknad.mal

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.dagpenger.søknad.SøknadMediator

internal fun Route.nyesteMalRoute(søknadMediator: SøknadMediator) {
    get("/mal") {
        val nyesteMal = søknadMediator.hentNyesteMal("Dagpenger")
        call.respond(nyesteMal.mal)
    }
}
