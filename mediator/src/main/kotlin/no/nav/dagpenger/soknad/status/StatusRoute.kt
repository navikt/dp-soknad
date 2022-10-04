package no.nav.dagpenger.soknad.status

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident

private val logger = KotlinLogging.logger {}
internal fun Route.statusRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)
    get("/{søknad_uuid}/status") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        validator.valider(søknadUuid, ident)

        val tilstand = søknadMediator.hentTilstand(søknadUuid)
        if (tilstand != null) {
            val søknadStatus = SøknadStatus(tilstand)
            logger.info { "Hentet status for søknad med id: $søknadUuid" }
            call.respond(HttpStatusCode.OK, søknadStatus)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}

data class SøknadStatus(val tilstand: Søknad.Tilstand.Type)
