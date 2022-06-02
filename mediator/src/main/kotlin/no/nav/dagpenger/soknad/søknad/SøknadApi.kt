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
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.Søknadsprosess.NySøknadsProsess
import no.nav.dagpenger.soknad.Søknadsprosess.PåbegyntSøknadsProsess
import no.nav.dagpenger.soknad.auth.ident
import no.nav.dagpenger.soknad.mottak.SøkerOppgave
import no.nav.dagpenger.soknad.søknad.ferdigstilling.ferdigstillingRoute
import no.nav.dagpenger.soknad.søknad.mal.nyesteMalRoute
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal fun søknadApiRouteBuilder(
    søknadMediator: SøknadMediator
): Route.() -> Unit = { søknadApi(søknadMediator) }

internal fun Route.søknadApi(
    søknadMediator: SøknadMediator
) {
    route("${Configuration.basePath}/soknad") {
        post {
            val ident = call.ident()

            val søknadsprosess = søknadMediator.hentEllerOpprettSøknadsprosess(ident)
            val svar = søknadsprosess.getSøknadsId()

            val statuskode = when (søknadsprosess) {
                is NySøknadsProsess -> HttpStatusCode.Created
                is PåbegyntSøknadsProsess -> HttpStatusCode.OK
            }

            call.response.header(HttpHeaders.Location, "${call.request.uri}/$svar/fakta")
            call.respondText(contentType = ContentType.Application.Json, statuskode) {
                svar.toString()
            }
        }

        get("/paabegynte") {
            call.respond(HttpStatusCode.OK, søknadMediator.hentPåbegynte(call.ident()))
        }
        get("/{søknad_uuid}/neste") {
            val id = søknadUuid()
            val ident = call.ident()
            val søkerOppgave: SøkerOppgave = hentNesteSeksjon(søknadMediator, id)
            if (ident != søkerOppgave.eier()) {
                throw IkkeTilgangExeption("Ikke tilgang til søknad")
            }
            call.respond(HttpStatusCode.OK, søkerOppgave.asFrontendformat())
        }
        put("/{søknad_uuid}/faktum/{faktumid}") {
            val søknadUuid = søknadUuid()
            val ident = call.ident()
            val faktumId = faktumId()
            val input = Svar(call.receive())
            logger.info { "Fikk \n${input.svarAsJson}" }

            val faktumSvar = FaktumSvar(
                søknadUuid = søknadUuid,
                faktumId = faktumId,
                type = input.type,
                eier = ident,
                svar = input.svarAsJson
            )

            søknadMediator.behandle(faktumSvar)
            call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { """{"status": "ok"}""" }
        }

        ferdigstillingRoute(søknadMediator)

        nyesteMalRoute(søknadMediator)
    }
}

class IkkeTilgangExeption(melding: String) : RuntimeException(melding)

private suspend fun hentNesteSeksjon(
    søknadMediator: SøknadMediator,
    id: UUID
) = retryIO(times = 10) { søknadMediator.hent(id) ?: throw NotFoundException("Fant ikke søknad med id $id") }

private fun PipelineContext<Unit, ApplicationCall>.søknadUuid() =
    call.parameters["søknad_uuid"].let { UUID.fromString(it) }
        ?: throw IllegalArgumentException("Må ha med id i parameter")

private fun PipelineContext<Unit, ApplicationCall>.faktumId(): String {
    val faktumId = call.parameters["faktumid"] ?: throw IllegalArgumentException("Må ha med id i parameter")
    require(kotlin.runCatching { faktumId.toInt() }.isSuccess) { "FaktumId må være et heltall" }
    return faktumId
}

suspend fun <T> retryIO(
    times: Int,
    initialDelay: Long = 100, // 0.1 second
    maxDelay: Long = 1000, // 1 second
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    var antallForsøk = 0
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            logger.warn { "Forsøk: ${++antallForsøk}/$times på henting av neste seksjon." }
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block() // last attempt
}
