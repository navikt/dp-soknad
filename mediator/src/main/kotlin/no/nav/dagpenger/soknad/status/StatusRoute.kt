package no.nav.dagpenger.soknad.status

import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hentSøknadUuidFraUrl
import no.nav.dagpenger.soknad.status.SøknadStatus.Paabegynt
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident
import no.nav.dagpenger.soknad.utils.auth.jwt
import java.time.LocalDate
import java.time.LocalDateTime

internal fun Route.statusRoute(søknadMediator: SøknadMediator, behandlingsstatusClient: BehandlingsstatusClient) {
    val validator = SøknadEierValidator(søknadMediator)

    get("/{søknad_uuid}/status") {
        val søknadUuid = hentSøknadUuidFraUrl()
        val ident = call.ident()
        val token = call.request.jwt()

        validator.valider(søknadUuid, ident)
        val søknadStatusResponse = withLoggingContext(
            "søknadId" to søknadUuid.toString(),
        ) {
            val søknad = søknadMediator.hent(søknadUuid)!!
            søknadStatusResponse(søknad, behandlingsstatusClient, token)
        }

        call.respond(OK, søknadStatusResponse)
    }
}

private suspend fun søknadStatusResponse(
    søknad: Søknad,
    behandlingsstatusClient: BehandlingsstatusClient,
    token: String,
) = when (søknad.tilstand().tilstandType) {
    Påbegynt -> SøknadStatusDto(Paabegynt, søknad.opprettetTidspunkt().toLocalDateTime())
    Innsendt -> {
        SøknadStatusDto(
            status = hentSøknadStatus(
                behandlingsstatusClient,
                førsteInnsendingTidspunkt = requireNotNull(søknad.innstendTidspunkt()).toLocalDate(),
                token,
            ),
            opprettet = søknad.opprettetTidspunkt().toLocalDateTime(),
            innsendt = requireNotNull(søknad.innstendTidspunkt()).toLocalDateTime(),
        )
    }

    UnderOpprettelse -> throw IllegalArgumentException("Kan ikke gi status for søknad som er under opprettelse")
    Slettet -> throw IllegalArgumentException("Kan ikke gi status for søknad som er slettet")
}

private suspend fun hentSøknadStatus(
    behandlingsstatusClient: BehandlingsstatusClient,
    førsteInnsendingTidspunkt: LocalDate,
    token: String,
): SøknadStatus {
    val behandlingsstatus =
        behandlingsstatusClient.hentBehandlingsstatus(fom = førsteInnsendingTidspunkt, token).behandlingsstatus
    return SøknadStatus.valueOf(behandlingsstatus)
}

data class SøknadStatusDto(
    val status: SøknadStatus,
    val opprettet: LocalDateTime,
    val innsendt: LocalDateTime? = null,
)
