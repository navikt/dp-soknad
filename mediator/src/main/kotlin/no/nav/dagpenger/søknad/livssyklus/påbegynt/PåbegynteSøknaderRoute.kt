package no.nav.dagpenger.søknad.livssyklus

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.utils.auth.ident

private val logger = KotlinLogging.logger {}

internal fun Route.påbegyntSøknadRoute(søknadMediator: SøknadMediator) {
    get("/paabegynt") {
        try {
            val påbegyntSøknad = søknadMediator.hentPåbegyntSøknad(call.ident())

            if (påbegyntSøknad != null) {
                logger.info { "$påbegyntSøknad returnert" }
                call.respond(HttpStatusCode.OK, påbegyntSøknad)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        } catch (e: Exception) {
            logger.error { "Noe gikk galt med henting av påbegynt søknad: $e" }
        }
    }
}
