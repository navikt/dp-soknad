package no.nav.dagpenger.quizshow.api

import com.auth0.jwk.JwkProvider
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.NotFoundException
import io.ktor.features.StatusPages
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.jackson.jackson
import io.ktor.request.document
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.delay
import mu.KotlinLogging
import no.nav.dagpenger.quizshow.api.Configuration.appName
import org.slf4j.event.Level
import java.net.URI

private val logger = KotlinLogging.logger {}

internal fun Application.søknadApi(
    jwkProvider: JwkProvider,
    issuer: String,
    clientId: String,
    store: SøknadStore
) {

    // As of https://tools.ietf.org/html/rfc7807
    data class HttpProblem(
        val type: URI = URI.create("about:blank"),
        val title: String,
        val status: Int? = 500,
        val detail: String? = null,
        val instance: URI = URI.create("about:blank")
    )

    install(CallLogging) {
        level = Level.DEBUG
        filter { call ->
            !setOf(
                "isalive",
                "isready",
                "metrics"
            ).contains(call.request.document())
        }
    }
    install(DefaultHeaders)
    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(
                InternalServerError,
                HttpProblem(title = "Feilet", detail = cause.message)
            )
        }
        exception<IllegalArgumentException> { cause ->
            call.respond(
                BadRequest,
                HttpProblem(title = "Klient feil", status = 400, detail = cause.message)
            )
        }
        exception<NotFoundException> { cause ->
            call.respond(
                NotFound,
                HttpProblem(title = "Ikke funnet", status = 404, detail = cause.message)
            )
        }
    }
    install(ContentNegotiation) {
        jackson {}
    }

    install(Authentication) {
        jwt {
            verifier(jwkProvider, issuer) {
                withAudience(clientId)
            }
            realm = appName
            validate { credentials ->
                requireNotNull(credentials.payload.claims["pid"]) {
                    "Token må inneholde fødselsnummer for personen"
                }

                JWTPrincipal(credentials.payload)
            }
        }
    }

    routing {
        authenticate {
            route("${Configuration.basePath}/soknad") {
                post {
                    val jwtPrincipal = call.authentication.principal<JWTPrincipal>()
                    val fnr = jwtPrincipal!!.fnr
                    val ønskerRettighetsavklaringMelding = ØnskerRettighetsavklaringMelding(fnr)
                    store.håndter(ønskerRettighetsavklaringMelding)
                    val svar = """{ "søknad_uuid" : "${ønskerRettighetsavklaringMelding.søknadUuid()}" }""".trimIndent()
                    call.respondText(contentType = Json, HttpStatusCode.Created) { svar }
                }
                get("/{søknad_uuid}/neste-seksjon") {
                    val id = søknadId()
                    val søknad = hent(store, id)
                    call.respondText(contentType = Json, HttpStatusCode.OK) { søknad }
                }
                get("/{søknad_uuid}/subsumsjoner") {
                    val id = søknadId()
                    val søknad = hent(store, id)
                    call.respondText(contentType = Json, HttpStatusCode.OK) { søknad }
                }
                put("/{søknad_uuid}/faktum/{faktumid}") {
                    val input = call.receiveText()
                    logger.info { "Fikk \n$input" }
                    call.respondText(
                        contentType = Json,
                        HttpStatusCode.OK
                    ) { """{"vet-ikke": "hvilket svar vi skal lage her"}""" }
                }
            }
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
