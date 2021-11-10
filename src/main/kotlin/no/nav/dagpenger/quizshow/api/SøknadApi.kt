package no.nav.dagpenger.quizshow.api

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
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
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.pipeline.PipelineContext
import mu.KotlinLogging
import java.net.URI

private val logger = KotlinLogging.logger {}

internal fun Application.søknadApi(store: SøknadStore) {

    // As https://tools.ietf.org/html/rfc7807
    data class HttpProblem(
        val type: URI = URI.create("about:blank"),
        val title: String,
        val status: Int? = 500,
        val detail: String? = null,
        val instance: URI = URI.create("about:blank")
    )

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

    routing {
        route("${Configuration.basePath}/soknad") {
            post {
                val ønskerRettighetsavklaringMelding = ØnskerRettighetsavklaringMelding("12345678901")
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
                call.respondText(contentType = Json, HttpStatusCode.OK) { """{"vet-ikke": "hvilket svar vi skal lage her"}""" }
            }
        }
    }
}

private fun hent(
    store: SøknadStore,
    id: String
) = store.hent(id) ?: throw NotFoundException("Fant ikke søknad med id $id")

private fun PipelineContext<Unit, ApplicationCall>.søknadId() =
    call.parameters["søknad_uuid"] ?: throw IllegalArgumentException("Må ha med id i parameter")
