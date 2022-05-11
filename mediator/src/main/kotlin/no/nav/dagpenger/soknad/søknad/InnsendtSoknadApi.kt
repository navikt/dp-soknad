package no.nav.dagpenger.soknad.søknad

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import io.ktor.util.pipeline.PipelineContext
import no.nav.dagpenger.soknad.db.InnsendtSoknadRepository
import java.util.UUID

internal fun Route.innsendtSoknadApi(
    db: InnsendtSoknadRepository
) {

    put("/{søknad_uuid}/ferdigstill/fulltekst") {
        val søknadUuid = søknadUuid()
        call.receiveText()
        db.lagre(søknadUuid, call.receiveText())
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun PipelineContext<Unit, ApplicationCall>.søknadUuid() =
    call.parameters["søknad_uuid"].let { UUID.fromString(it) }
        ?: throw IllegalArgumentException("Må ha med id i parameter")
