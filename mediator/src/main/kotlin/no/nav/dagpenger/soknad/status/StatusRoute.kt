package no.nav.dagpenger.soknad.status

import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.status.SøknadStatus.Paabegynt
import no.nav.dagpenger.soknad.status.SøknadStatus.Ukjent
import no.nav.dagpenger.soknad.status.SøknadStatus.UnderBehandling
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}
internal fun Route.statusRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)
    get("/{søknad_uuid}/status") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        try {
            validator.valider(søknadUuid, ident)
            val søknad = søknadMediator.hent(søknadUuid)!!
            val søknadStatusVisitor = SøknadStatusVisitor(søknad)

            when (søknadStatusVisitor.søknadTilstand) {
                UnderOpprettelse -> call.respond(InternalServerError)
                Påbegynt -> call.respond(status = OK, søknadStatusVisitor.søknadStatus)
                Innsendt -> call.respond(
                    status = OK,
                    SøknadStatusDTO(UnderBehandling, opprettet = LocalDateTime.MAX, innsendt = LocalDateTime.MAX)
                )
                // TODO: Søknad med tilstand slettet kaster IllegalArgumentException ved rehydrering, returnerer derfor 404
                Slettet -> call.respond(NotFound)
            }
        } catch (e: IllegalArgumentException) {
            logger.info { "Fant ikke søknad med $søknadUuid" }
            call.respond(NotFound)
        }
    }
}

private class SøknadStatusVisitor(søknad: Søknad) : SøknadVisitor {

    lateinit var søknadStatus: SøknadStatusDTO
    lateinit var søknadTilstand: Søknad.Tilstand.Type

    init {
        søknad.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        søknadTilstand = tilstand.tilstandType
        søknadStatus = SøknadStatusDTO(
            status = avgjørStatus(tilstand),
            opprettet = opprettet.toLocalDateTime(),
            innsendt = null
        )
    }

    private fun avgjørStatus(tilstand: Søknad.Tilstand): SøknadStatus {
        return when (tilstand.tilstandType) {
            Påbegynt -> Paabegynt
            Innsendt -> UnderBehandling // TODO: ikke hardkode en enkelt verdi
            else -> Ukjent
        }
    }
}

data class SøknadStatusDTO(
    val status: SøknadStatus,
    val opprettet: LocalDateTime,
    val innsendt: LocalDateTime? = null,
)
