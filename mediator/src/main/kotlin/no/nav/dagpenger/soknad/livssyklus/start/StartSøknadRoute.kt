package no.nav.dagpenger.soknad.livssyklus.start

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.auth.ident
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal fun Route.startSøknadRoute(søknadMediator: SøknadMediator) {
    post {
        val ident = call.ident()
        val språk = call.request.queryParameters["spraak"] ?: "NB"
        val prosesstype =
            call.request.queryParameters["prosesstype"] ?: "Dagpenger"
        val prosessnavn = søknadMediator.prosessnavn(prosesstype)
        val søknadId = call.request.queryParameters["søknadId"]?.let { UUID.fromString(it) } ?: UUID.randomUUID()

        val søknadsprosess = søknadMediator.opprettSøknadsprosess(ident, språk, prosessnavn, søknadId)
        val søknadUuid = søknadsprosess.getSøknadsId()

        logger.info { "Opprettet søknadsprosess med id: $søknadUuid" }

        call.response.header(HttpHeaders.Location, "${call.request.uri}/$søknadUuid/fakta")
        call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.Created) {
            søknadUuid.toString()
        }
    }
}
