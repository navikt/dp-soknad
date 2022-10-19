package no.nav.dagpenger.soknad.mineSoknader

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.status.SøknadStatusVisitor
import no.nav.dagpenger.soknad.utils.auth.ident
import java.time.LocalDateTime
import java.util.UUID

internal fun Route.mineSoknaderRoute(søknadMediator: SøknadMediator) {
    get("/mineSoknader") {
        // val fom = call.request.queryParameters["fom"] ?: throw IllegalArgumentException("Mangler fom queryparameter i url")
        val ident = call.ident()

        val søknader = søknadMediator.hentSøknader(ident)
        var påbegyntSøknad: PaabegyntSoknad? = null
        val innsendteSøknader = mutableListOf<InnsendtSoknad>()

        søknader.map { søknad ->
            val visitor = SøknadStatusVisitor(søknad)
            when {
                visitor.søknadTilstand() == Påbegynt -> {
                    påbegyntSøknad = PaabegyntSoknad(søknad.søknadUUID(), visitor.søknadOpprettet())
                }
                visitor.søknadTilstand() == Innsendt -> {
                    innsendteSøknader.add(
                        InnsendtSoknad(søknad.søknadUUID(), visitor.førsteInnsendingTidspunkt())
                    )
                }
                else -> {}
            }
        }

        call.respond(
            HttpStatusCode.OK,
            MineSoknaderDto(påbegyntSøknad, innsendteSøknader.ifEmpty { null })
        )
    }
}

data class MineSoknaderDto(val paabegynt: PaabegyntSoknad?, val innsendte: List<InnsendtSoknad>?)

data class PaabegyntSoknad(val soknadUuid: UUID, val opprettet: LocalDateTime)

data class InnsendtSoknad(val soknadUuid: UUID, val forstInnsendt: LocalDateTime)
