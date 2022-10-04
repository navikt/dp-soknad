package no.nav.dagpenger.soknad.status

import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident

private val logger = KotlinLogging.logger {}
internal fun Route.tilstandRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)
    get("/{søknad_uuid}/tilstand") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        validator.valider(søknadUuid, ident)

        val tilstand = søknadMediator.hentTilstand(søknadUuid)
        logger.info { "Tilstand på søknad med id $søknadUuid: $tilstand" }
        val søknadTilstand = SøknadTilstand(tilstand?.name)

        when (tilstand) {
            UnderOpprettelse -> call.respond(status = InternalServerError, søknadTilstand)
            Påbegynt -> call.respond(status = OK, søknadTilstand)
            Innsendt -> call.respond(status = OK, søknadTilstand)
            Slettet -> call.respond(status = NotFound, søknadTilstand)
            null -> call.respond(message = NotFound)
        }
    }
}

data class SøknadTilstand(var tilstand: String?) {
    init {
        if (tilstand == Påbegynt.name) tilstand = "Paabegynt"
    }
}
