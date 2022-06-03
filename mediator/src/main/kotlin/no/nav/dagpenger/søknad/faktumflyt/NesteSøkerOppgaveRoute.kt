package no.nav.dagpenger.søknad.faktumflyt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.util.pipeline.PipelineContext
import no.nav.dagpenger.søknad.IkkeTilgangExeption
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.auth.ident
import java.util.UUID

internal fun Route.nesteSøkerOppgaveRoute(søknadMediator: SøknadMediator) {
    get("/{søknad_uuid}/neste") {
        val id = søknadUuid()
        val ident = call.ident()
        val søkerOppgave: SøkerOppgave = hentNesteSøkerOppgave(søknadMediator, id)
        if (ident != søkerOppgave.eier()) {
            throw IkkeTilgangExeption("Ikke tilgang til søknad")
        }
        call.respond(HttpStatusCode.OK, søkerOppgave.asFrontendformat())
    }
}

private suspend fun hentNesteSøkerOppgave(søknadMediator: SøknadMediator, id: UUID) =
    retryIO(times = 10) { søknadMediator.hent(id) ?: throw NotFoundException("Fant ikke søknad med id $id") }

private fun PipelineContext<Unit, ApplicationCall>.søknadUuid() =
    call.parameters["søknad_uuid"].let { UUID.fromString(it) }
        ?: throw IllegalArgumentException("Må ha med id i parameter")
