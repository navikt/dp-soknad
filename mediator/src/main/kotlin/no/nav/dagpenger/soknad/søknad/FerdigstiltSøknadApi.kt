package no.nav.dagpenger.soknad.søknad

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.db.FerdigstiltSøknadRepository
import java.util.UUID

internal fun ferdigStiltSøknadRouteBuilder(db: FerdigstiltSøknadRepository): Route.() -> Unit {
    return {
        ferdigstiltSøknadsApi(db)
    }
}

internal fun Route.ferdigstiltSøknadsApi(
    db: FerdigstiltSøknadRepository
) {
    route("${Configuration.basePath}/{søknad_uuid}/ferdigstilt") {

        get("/tekst") {
            call.respondText(
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
                text = db.hentTekst(søknadUuid())
            )
        }

        get("/fakta") {
            call.respondText(
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
                text = db.hentFakta(søknadUuid())
            )
        }
    }
}

private fun PipelineContext<Unit, ApplicationCall>.søknadUuid() =
    call.parameters["søknad_uuid"].let { UUID.fromString(it) }
        ?: throw IllegalArgumentException("Må ha med id i parameter")
