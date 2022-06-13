package no.nav.dagpenger.søknad.livssyklus.ferdigstilling

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.dagpenger.søknad.Configuration
import no.nav.dagpenger.søknad.søknadUuid

internal fun ferdigStiltSøknadRouteBuilder(ferdigstiltSøknadDb: FerdigstiltSøknadPostgresRepository): Route.() -> Unit {
    return {
        ferdigstiltSøknadsApi(ferdigstiltSøknadDb)
    }
}

internal fun Route.ferdigstiltSøknadsApi(ferdigstiltSøknadDb: FerdigstiltSøknadPostgresRepository) {
    route("${Configuration.basePath}/{søknad_uuid}/ferdigstilt") {

        get("/tekst") {
            call.respondText(
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
                text = ferdigstiltSøknadDb.hentTekst(søknadUuid())
            )
        }
        get("/fakta") {
            call.respondText(
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
                text = ferdigstiltSøknadDb.hentFakta(søknadUuid())
            )
        }
    }
}
