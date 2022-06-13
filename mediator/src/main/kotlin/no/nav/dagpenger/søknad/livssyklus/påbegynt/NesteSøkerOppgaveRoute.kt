package no.nav.dagpenger.søknad.livssyklus.påbegynt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.dagpenger.søknad.IkkeTilgangExeption
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.søknadUuid
import no.nav.dagpenger.søknad.utils.auth.ident
import java.util.UUID

internal fun Route.nesteSøkeroppgaveRoute(søknadMediator: SøknadMediator) {
    get("/{søknad_uuid}/neste") {
        val id = søknadUuid()
        val ident = call.ident()
        try {
            val søkerOppgave: SøkerOppgave = hentNesteSøkerOppgave(søknadMediator, id)
            if (ident != søkerOppgave.eier()) {
                throw IkkeTilgangExeption("Ikke tilgang til søknad")
            }
            call.respond(HttpStatusCode.OK, søkerOppgave.asFrontendformat())
        } catch (e: NotFoundException) {
            throw e
        }
    }
}

private suspend fun hentNesteSøkerOppgave(søknadMediator: SøknadMediator, id: UUID) =
    retryIO(times = 15) { søknadMediator.hent(id) ?: throw NotFoundException("Fant ikke søker_oppgave for søknad med id $id") }
