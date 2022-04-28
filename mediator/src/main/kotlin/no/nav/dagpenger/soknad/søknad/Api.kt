package no.nav.dagpenger.soknad.søknad

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.delay
import mu.KLogger
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.auth.ident
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import java.util.UUID

internal fun Route.api(logger: KLogger, store: SøknadStore, søknadMediator: SøknadMediator) {
    route("${Configuration.basePath}/soknad") {
        post {
            val ident = call.ident()
            val ønskeOmNySøknadHendelse = ØnskeOmNySøknadHendelse(ident, søknadID = UUID.randomUUID())
            søknadMediator.behandle(ønskeOmNySøknadHendelse)
            val svar = ønskeOmNySøknadHendelse.søknadID()
            call.response.header(HttpHeaders.Location, "${call.request.uri}/$svar/fakta")
            call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.Created) {
                svar.toString()
            }
        }
        get("/{søknad_uuid}/fakta") {
            val id = søknadUuid()
            val ident = call.ident()
            val søknad: Søknad = hentFakta(store, id)
            if (ident != søknad.eier()) {
                throw IkkeTilgangExeption("Ikke tilgang til søknad")
            }
            call.respond(HttpStatusCode.OK, søknad.fakta())
        }
        put("/{søknad_uuid}/faktum/{faktumid}") {
            val søknadUuid = søknadUuid()
            val faktumId = faktumId()
            val input = Svar(call.receive())
            logger.info { "Fikk \n${input.svarAsJson}" }

            val faktumSvar = FaktumSvar(
                søknadUuid = søknadUuid,
                faktumId = faktumId,
                type = input.type,
                svar = input.svarAsJson
            )

            store.håndter(faktumSvar)
            call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { """{"status": "ok"}""" }
        }

        put("/{søknad_uuid}/ferdigstill") {
            val søknadUuid = søknadUuid()
            val ident = call.ident()
            val søknadInnsendtHendelse = SøknadInnsendtHendelse(søknadUuid, ident)
            søknadMediator.behandle(søknadInnsendtHendelse)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

class IkkeTilgangExeption(melding: String) : RuntimeException(melding)

private suspend fun hentFakta(
    store: SøknadStore,
    id: UUID
) = retryIO(times = 10) { store.hentFakta(id) ?: throw NotFoundException("Fant ikke søknad med id $id") }

private fun PipelineContext<Unit, ApplicationCall>.søknadUuid() =
    call.parameters["søknad_uuid"].let { UUID.fromString(it) } ?: throw IllegalArgumentException("Må ha med id i parameter")

private fun PipelineContext<Unit, ApplicationCall>.faktumId(): String {
    val faktumId = call.parameters["faktumid"] ?: throw IllegalArgumentException("Må ha med id i parameter")
    require(kotlin.runCatching { faktumId.toInt() }.isSuccess) { "FaktumId må være et heltall" }
    return faktumId
}

suspend fun <T> retryIO(
    times: Int = Int.MAX_VALUE,
    initialDelay: Long = 100, // 0.1 second
    maxDelay: Long = 1000, // 1 second
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            // you can log an error here and/or make a more finer-grained
            // analysis of the cause to see if retry is needed
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block() // last attempt
}
