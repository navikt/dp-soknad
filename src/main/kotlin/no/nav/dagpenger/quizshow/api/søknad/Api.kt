package no.nav.dagpenger.quizshow.api.søknad

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.NotFoundException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.request.uri
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.delay
import mu.KLogger
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.Søknad
import no.nav.dagpenger.quizshow.api.SøknadStore
import no.nav.dagpenger.quizshow.api.auth.ident
import no.nav.helse.rapids_rivers.JsonMessage
import java.time.LocalDateTime
import java.util.UUID

internal fun Route.api(logger: KLogger, store: SøknadStore) {
    route("${Configuration.basePath}/soknad") {
        post {
            val ident = call.ident()
            val nySøknadMelding = NySøknadMelding(ident)
            store.håndter(nySøknadMelding)
            val svar = nySøknadMelding.søknadUuid
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
    }
}

class IkkeTilgangExeption(melding: String) : RuntimeException(melding)

internal data class NySøknadMelding(val fødselsnummer: String) {
    private val navn = "NySøknad"
    private val opprettet = LocalDateTime.now()
    private val id = UUID.randomUUID()
    internal val søknadUuid = UUID.randomUUID()

    fun toJson() = JsonMessage.newMessage(
        mutableMapOf(
            "@event_name" to navn,
            "@opprettet" to opprettet,
            "@id" to id,
            "søknad_uuid" to søknadUuid,
            "fødselsnummer" to fødselsnummer,
        )
    ).toJson()
}

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
