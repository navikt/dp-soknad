package no.nav.dagpenger.soknad.status

import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.status.SøknadStatus.Paabegynt
import no.nav.dagpenger.soknad.status.SøknadStatus.Ukjent
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident
import no.nav.dagpenger.soknad.utils.auth.jwt
import java.time.LocalDate
import java.time.LocalDateTime

internal fun Route.statusRoute(søknadMediator: SøknadMediator, behandlingsstatusClient: BehandlingsstatusClient) {
    val validator = SøknadEierValidator(søknadMediator)

    get("/{søknad_uuid}/status") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        val token = call.request.jwt()

        validator.valider(søknadUuid, ident)

        val søknadStatusDto = withLoggingContext(
            "søknadId" to søknadUuid.toString()
        ) {
            val søknad = søknadMediator.hent(søknadUuid)!!
            val søknadStatusVisitor = SøknadStatusVisitor(søknad)
            createSøknadStatusDto(søknadStatusVisitor, behandlingsstatusClient, token)
        }

        call.respond(OK, søknadStatusDto)
    }
}

private suspend fun createSøknadStatusDto(
    statusVisitor: SøknadStatusVisitor,
    behandlingsstatusClient: BehandlingsstatusClient,
    token: String,
) = when (statusVisitor.søknadTilstand()) {
    Påbegynt -> SøknadStatusDto(Paabegynt, statusVisitor.søknadOpprettet())
    Innsendt -> {
        SøknadStatusDto(
            status = hentSøknadStatus(
                behandlingsstatusClient,
                førsteInnsendingTidspunkt = statusVisitor.søknadInnsendt().toLocalDate(),
                token
            ),
            opprettet = statusVisitor.søknadOpprettet(),
            innsendt = statusVisitor.søknadInnsendt().toLocalDateTime()
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
    return if (behandlingsstatus != "null") SøknadStatus.valueOf(behandlingsstatus) else Ukjent
}

data class SøknadStatusDto(
    val status: SøknadStatus,
    val opprettet: LocalDateTime,
    val innsendt: LocalDateTime? = null,
)
