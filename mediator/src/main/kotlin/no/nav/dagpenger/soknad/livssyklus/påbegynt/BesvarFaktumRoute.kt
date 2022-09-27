package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import io.ktor.util.pipeline.PipelineContext
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

internal fun Route.besvarFaktumRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)
    put("/{søknad_uuid}/faktum/{faktumid}") {
        val tidBrukt = measureTimeMillis {
            val søknadUuid = søknadUuid()
            val ident = call.ident()
            val faktumId = faktumId()
            withLoggingContext("søknadid" to søknadUuid.toString()) {
                validator.valider(søknadUuid, ident)
                val input = GyldigSvar(call.receive())
                logger.info { "Besvarer faktum= $faktumId, type=${input.type} svar=${if (input.type !== "tekst") input.svarAsJson.toString() else "Viser ikke svar på fritekst"}" }
                val faktumSvar = FaktumSvar(
                    søknadUuid = søknadUuid,
                    faktumId = faktumId,
                    type = input.type,
                    eier = ident,
                    svar = input.svarAsJson
                )

                søknadMediator.behandle(faktumSvar)
            }
            call.respond(BesvartFaktum("ok", LocalDateTime.now()))
        }

        logger.info { "Brukte $tidBrukt ms på å håndtere faktumSvar" }
    }
}

private data class BesvartFaktum(
    val status: String,
    val sistBesvart: LocalDateTime
)

private fun PipelineContext<Unit, ApplicationCall>.faktumId(): String {
    val faktumId = call.parameters["faktumid"] ?: throw IllegalArgumentException("Må ha med id i parameter")
    require(kotlin.runCatching { faktumId.toDouble() }.isSuccess) { "FaktumId må være et heltall eller desimaltall. Var '$faktumId'" }
    return faktumId
}
