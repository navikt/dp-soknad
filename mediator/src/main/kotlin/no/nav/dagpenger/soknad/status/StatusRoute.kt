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
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.dagpenger.soknad.status.SøknadStatus.Paabegynt
import no.nav.dagpenger.soknad.status.SøknadStatus.UnderBehandling
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident
import no.nav.dagpenger.soknad.utils.auth.jwt
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal fun Route.statusRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)

    get("/{søknad_uuid}/status") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        val token = call.request.jwt()

        try {
            validator.valider(søknadUuid, ident)
            val søknad = søknadMediator.hent(søknadUuid)!!
            val statusVisitor = SøknadStatusVisitor(søknad)

            when (statusVisitor.søknadTilstand()) {
                UnderOpprettelse -> call.respond(InternalServerError)
                Påbegynt -> call.respond(status = OK, SøknadStatusDTO(Paabegynt, statusVisitor.søknadOpprettet()))
                Innsendt -> call.respond(
                    status = OK,
                    SøknadStatusDTO(
                        status = UnderBehandling,
                        opprettet = statusVisitor.søknadOpprettet(),
                        innsendt = statusVisitor.førsteInnsendingTidspunkt()
                    )
                )
                // TODO: Søknad med tilstand slettet kaster IllegalArgumentException ved rehydrering, returnerer derfor 500
                Slettet -> call.respond(NotFound)
            }
        } catch (e: IllegalArgumentException) {
            logger.info { "Fant ikke søknad med $søknadUuid" }
            call.respond(NotFound)
        }
    }
}

private suspend fun søknadStatus(
    behandlingsstatusClient: BehandlingsstatusHttpClient,
    førsteInnsendingTidspunkt: LocalDateTime,
    token: String
) = SøknadStatus.valueOf(
    behandlingsstatusClient.hentBehandlingsstatus(
        fom = førsteInnsendingTidspunkt,
        token
    ).behandlingsstatus
)

private class SøknadStatusVisitor(søknad: Søknad) : SøknadVisitor {

    private lateinit var søknadOpprettet: LocalDateTime
    private lateinit var søknadTilstand: Søknad.Tilstand.Type
    private val søknadInnsendinger: MutableList<LocalDateTime> = mutableListOf()

    init {
        søknad.accept(this)
    }

    fun førsteInnsendingTidspunkt() = søknadInnsendinger.minOf { it }
    fun søknadOpprettet() = søknadOpprettet
    fun søknadTilstand() = søknadTilstand

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        søknadOpprettet = opprettet.toLocalDateTime()
        søknadTilstand = tilstand.tilstandType
    }

    override fun visit(
        innsendingId: UUID,
        innsending: Innsending.InnsendingType,
        tilstand: Innsending.TilstandType,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Innsending.Dokument?,
        dokumenter: List<Innsending.Dokument>,
        brevkode: Innsending.Brevkode?
    ) {
        søknadInnsendinger.add(innsendt.toLocalDateTime())
    }
}

data class SøknadStatusDTO(
    val status: SøknadStatus,
    val opprettet: LocalDateTime,
    val innsendt: LocalDateTime? = null,
)
