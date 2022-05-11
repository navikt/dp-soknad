package no.nav.dagpenger.soknad.søknad

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.util.pipeline.PipelineContext
import no.nav.dagpenger.soknad.db.FerdigstiltSøknadRepository
import java.util.UUID

internal fun Route.ferdigstiltSøknadsApi(
    db: FerdigstiltSøknadRepository
) {

    get("/{søknad_uuid}/ferdigstilt/tekst") {
        call.respondText(
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK,
            text = db.hentTekst(søknadUuid())
        )
    }
}

private fun PipelineContext<Unit, ApplicationCall>.søknadUuid() =
    call.parameters["søknad_uuid"].let { UUID.fromString(it) }
        ?: throw IllegalArgumentException("Må ha med id i parameter")
