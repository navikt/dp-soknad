package no.nav.dagpenger.søknad.livssyklus.ferdigstilling

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import io.ktor.util.pipeline.PipelineContext
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.søknad.utils.auth.ident
import java.util.UUID

internal fun Route.ferdigstillSøknadRoute(søknadMediator: SøknadMediator) {
    put("/{søknad_uuid}/ferdigstill") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        val søknadInnsendtHendelse = SøknadInnsendtHendelse(søknadUuid, ident)
        call.receive<JsonNode>().let {
            søknadMediator.lagreSøknadsTekst(søknadUuid, it.toString())
        }
        søknadMediator.behandle(søknadInnsendtHendelse)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun PipelineContext<Unit, ApplicationCall>.søknadUuid() =
    call.parameters["søknad_uuid"].let { UUID.fromString(it) }
        ?: throw IllegalArgumentException("Må ha med id i parameter")
