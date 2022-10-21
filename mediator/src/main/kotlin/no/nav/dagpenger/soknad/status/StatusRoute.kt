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
import no.nav.dagpenger.soknad.SøknadDataVisitor
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.status.SøknadStatus.Paabegynt
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident
import no.nav.dagpenger.soknad.utils.auth.jwt
import java.time.LocalDate
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

internal fun Route.statusRoute(søknadMediator: SøknadMediator, behandlingsstatusClient: BehandlingsstatusClient) {
    val validator = SøknadEierValidator(søknadMediator)

    get("/{søknad_uuid}/status") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        val token = call.request.jwt()

        try {
            validator.valider(søknadUuid, ident)
            val søknad = søknadMediator.hent(søknadUuid)!!
            val søknadData = SøknadDataVisitor(søknad)

            when (søknadData.søknadTilstand()) {
                UnderOpprettelse -> call.respond(InternalServerError)
                Påbegynt -> call.respond(status = OK, SøknadStatusDTO(Paabegynt, søknadData.søknadOpprettet()))
                Innsendt -> {
                    val førsteInnsendingTidspunkt = søknadData.førsteInnsendingTidspunkt()
                    call.respond(
                        status = OK,
                        SøknadStatusDTO(
                            status = søknadStatus(behandlingsstatusClient, førsteInnsendingTidspunkt.toLocalDate(), token),
                            opprettet = søknadData.søknadOpprettet(),
                            innsendt = førsteInnsendingTidspunkt
                        )
                    )
                }
                // TODO: Søknad med tilstand slettet kaster IllegalArgumentException ved rehydrering, returnerer derfor 500
                Slettet -> call.respond(NotFound)
            }
        } catch (e: IllegalArgumentException) {
            logger.info { "Fant ikke søknad med $søknadUuid. Error: ${e.message}" }
            call.respond(NotFound)
        }
    }
}

private suspend fun søknadStatus(
    behandlingsstatusClient: BehandlingsstatusClient,
    førsteInnsendingTidspunkt: LocalDate,
    token: String
): SøknadStatus {
    val behandlingsstatus =
        behandlingsstatusClient.hentBehandlingsstatus(fom = førsteInnsendingTidspunkt, token).behandlingsstatus
    return SøknadStatus.valueOf(behandlingsstatus)
}

data class SøknadStatusDTO(
    val status: SøknadStatus,
    val opprettet: LocalDateTime,
    val innsendt: LocalDateTime? = null,
)
