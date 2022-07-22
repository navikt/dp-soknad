package no.nav.dagpenger.soknad.livssyklus.påbegynt

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
import mu.withLoggingContext
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.ident

private val logger = KotlinLogging.logger {}

internal fun Route.besvarFaktumRoute(søknadMediator: SøknadMediator) {
    put("/{søknad_uuid}/faktum/{faktumid}") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        val faktumId = faktumId()
        withLoggingContext("søknadid" to søknadUuid.toString()) {
            val input = GyldigSvar(call.receive())
            logger.info { "Besvarer faktum= $faktumId, type=${input.type} svar=${if (input.type !== "tekst") input.svarAsJson else "Viser ikke svar på fritekst"}" }
            val faktumSvar = FaktumSvar(
                søknadUuid = søknadUuid,
                faktumId = faktumId,
                type = input.type,
                eier = ident,
                svar = input.svarAsJson
            )

            søknadMediator.behandle(faktumSvar)
        }
        call.respondText(contentType = ContentType.Application.Json, HttpStatusCode.OK) { """{"status": "ok"}""" }
    }
}

private fun PipelineContext<Unit, ApplicationCall>.faktumId(): String {
    val faktumId = call.parameters["faktumid"] ?: throw IllegalArgumentException("Må ha med id i parameter")
    require(kotlin.runCatching { faktumId.toDouble() }.isSuccess) { "FaktumId må være et heltall eller desimaltall. Var '$faktumId'" }
    return faktumId
}
