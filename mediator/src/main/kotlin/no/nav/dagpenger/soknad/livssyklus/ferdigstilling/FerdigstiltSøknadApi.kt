package no.nav.dagpenger.soknad.livssyklus.ferdigstilling

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.søknadUuid

internal fun ferdigStiltSøknadRouteBuilder(ferdigstiltSøknadDb: FerdigstiltSøknadPostgresRepository): Route.() -> Unit {
    return {
        ferdigstiltSøknadsApi(ferdigstiltSøknadDb)
    }
}

internal fun Route.ferdigstiltSøknadsApi(ferdigstiltSøknadDb: FerdigstiltSøknadPostgresRepository) {
    route("${Configuration.basePath}/{søknad_uuid}/ferdigstilt") {
        get("/tekst") {
            withLoggingContext("søknadId" to søknadUuid().toString()) {
                call.respondText(
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                    text = ferdigstiltSøknadDb.hentTekst(søknadUuid()),
                )
            }
        }
        get("/fakta") {
            withLoggingContext("søknadId" to søknadUuid().toString()) {
                call.respondText(
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK,
                    text = ferdigstiltSøknadDb.hentFakta(søknadUuid()),
                )
            }
        }
    }
}
