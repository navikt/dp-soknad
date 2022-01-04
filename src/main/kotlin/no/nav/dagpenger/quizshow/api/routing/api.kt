package no.nav.dagpenger.quizshow.api.routing

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.features.NotFoundException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.delay
import mu.KotlinLogging
import no.nav.dagpenger.quizshow.api.FaktumSvar
import no.nav.dagpenger.quizshow.api.SøknadStore
import no.nav.dagpenger.quizshow.api.ØnskerRettighetsavklaringMelding

private val logger = KotlinLogging.logger {}

internal fun Route.soknadApi(store: SøknadStore) {
    route("${no.nav.dagpenger.quizshow.api.Configuration.basePath}/soknad") {
        post {
            val jwtPrincipal = call.authentication.principal<JWTPrincipal>()
            val fnr = jwtPrincipal!!.fnr
            val ønskerRettighetsavklaringMelding = ØnskerRettighetsavklaringMelding(fnr)
            store.håndter(ønskerRettighetsavklaringMelding)
            val svar = """{ "søknad_uuid" : "${ønskerRettighetsavklaringMelding.søknadUuid()}" }""".trimIndent()
            call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.Created) { svar }
        }
        get("/{søknad_uuid}/neste-seksjon") {
            val id = søknadId()
            val søknad = hent(store, id)
            call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { søknad }
        }
        get("/{søknad_uuid}/subsumsjoner") {
            val id = søknadId()
            val søknad = hent(store, id)
            call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { søknad }
        }
        put("/{søknad_uuid}/faktum/{faktumid}") {
            val id = søknadId()
            val faktumId = faktumId()
            val input = call.receive<Svar>()
            logger.info { "Fikk \n$input" }
            input.valider()

            val faktumSvar = FaktumSvar(
                søknadUuid = java.util.UUID.fromString(id),
                faktumId = faktumId,
                clazz = input.type,
                svar = input.svar
            )

            store.håndter(faktumSvar)
            call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { """{"status": "ok"}""" }
        }
    }
}

private val JWTPrincipal.fnr get() = this.payload.claims["pid"]!!.asString()

private suspend fun hent(
    store: SøknadStore,
    id: String
) = retryIO(times = 10) { store.hent(id) ?: throw NotFoundException("Fant ikke søknad med id $id") }

private fun PipelineContext<Unit, ApplicationCall>.søknadId() =
    call.parameters["søknad_uuid"] ?: throw IllegalArgumentException("Må ha med id i parameter")

private fun PipelineContext<Unit, ApplicationCall>.faktumId() =
    call.parameters["faktumid"] ?: throw IllegalArgumentException("Må ha med id i parameter")

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
