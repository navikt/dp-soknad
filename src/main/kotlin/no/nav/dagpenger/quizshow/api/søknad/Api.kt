package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
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
import no.nav.dagpenger.quizshow.api.SøknadStore
import no.nav.dagpenger.quizshow.api.auth.fnr
import no.nav.helse.rapids_rivers.JsonMessage
import java.time.LocalDateTime
import java.util.UUID

internal fun Route.api(logger: KLogger, store: SøknadStore) {
    route("${Configuration.basePath}/soknad") {
        post {
            val jwtPrincipal = call.authentication.principal<JWTPrincipal>()
            val fnr = jwtPrincipal!!.fnr
            val nySøknadMelding = NySøknadMelding(fnr)
            store.håndter(nySøknadMelding)
            val svar = nySøknadMelding.søknadUuid
            call.response.header(HttpHeaders.Location, "${call.request.uri}/$svar/fakta")
            call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.Created) {
                svar.toString()
            }
        }
        get("/{søknad_uuid}/fakta") {
            val id = søknadId()
            val søknad: JsonNode = hentFakta(store, id)
            call.respond(HttpStatusCode.OK, søknad["fakta"])
        }
        put("/{søknad_uuid}/faktum/{faktumid}") {
            val id = søknadId()
            val faktumId = faktumId()
            val input = Svar(call.receive())
            logger.info { "Fikk \n${input.jsonNode}" }

            val faktumSvar = FaktumSvar(
                søknadUuid = UUID.fromString(id),
                faktumId = faktumId,
                type = input.type,
                svar = input.jsonNode
            )

            store.håndter(faktumSvar)
            call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { """{"status": "ok"}""" }
        }
    }
}

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
    id: String
) = retryIO(times = 10) { store.hentFakta(id) ?: throw NotFoundException("Fant ikke søknad med id $id") }

private fun PipelineContext<Unit, ApplicationCall>.søknadId() =
    call.parameters["søknad_uuid"] ?: throw IllegalArgumentException("Må ha med id i parameter")

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
