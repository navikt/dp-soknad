package no.nav.dagpenger.søknad.faktumflyt

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import io.ktor.util.pipeline.PipelineContext
import mu.KotlinLogging
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.auth.ident
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal fun Route.besvarFaktumRoute(søknadMediator: SøknadMediator) {
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
}

private fun PipelineContext<Unit, ApplicationCall>.søknadUuid() =
    call.parameters["søknad_uuid"].let { UUID.fromString(it) }
        ?: throw IllegalArgumentException("Må ha med id i parameter")

private fun PipelineContext<Unit, ApplicationCall>.faktumId(): String {
    val faktumId = call.parameters["faktumid"] ?: throw IllegalArgumentException("Må ha med id i parameter")
    require(kotlin.runCatching { faktumId.toInt() }.isSuccess) { "FaktumId må være et heltall" }
    return faktumId
}
