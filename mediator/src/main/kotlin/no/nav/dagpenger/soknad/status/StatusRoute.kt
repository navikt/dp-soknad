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
import no.nav.dagpenger.soknad.status.SøknadStatus.Paabegynt
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}
internal fun Route.statusRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)
    get("/{søknad_uuid}/status") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        validator.valider(søknadUuid, ident)

        val tilstand = søknadMediator.hentTilstand(søknadUuid)
        logger.info { "Tilstand på søknad med id $søknadUuid: $tilstand" }
        val søknadOpprettet = søknadMediator.hentOpprettet(søknadUuid)!!

        when (tilstand) {
            UnderOpprettelse -> call.respond(InternalServerError)
            Påbegynt -> call.respond(status = OK, SøknadStatusDTO(Paabegynt, soknadOpprettet = søknadOpprettet))
            Innsendt -> call.respond(status = OK, SøknadStatusOld(tilstand.name))
            Slettet -> call.respond(NotFound)
            null -> call.respond(message = NotFound)
        }
    }
}

data class SøknadStatusOld(var tilstand: String?) {
    init {
        if (tilstand == Påbegynt.name) tilstand = "Paabegynt"
    }
}

data class SøknadStatusDTO(
    val status: SøknadStatus,
    val soknadOpprettet: LocalDateTime,
    val innsendtDato: LocalDateTime? = null,
)
