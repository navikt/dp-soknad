package no.nav.dagpenger.soknad.livssyklus.ferdigstilling

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident

private val logger = KotlinLogging.logger {}

internal fun Route.ferdigstillSøknadRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)
    put("/{søknad_uuid}/ferdigstill") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        withLoggingContext("søknadid" to søknadUuid.toString()) {
            validator.valider(søknadUuid, ident)
            val søknadstekstJson = call.receive<JsonNode>()
            val søknadInnsendtHendelse = SøknadInnsendtHendelse(søknadUuid, ident)
            try {
                søknadMediator.behandleSøknadInnsendtHendelse(søknadInnsendtHendelse)
                søknadMediator.lagreSøknadsTekst(søknadUuid, søknadstekstJson.toString())
            } catch (err: Aktivitetslogg.AktivitetException) {
                logger.error(err) { "Kunne ikke behandle SøknadInnsendtHendelse, faktum eller dokumentkrav mangler" }
                throw err
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }

    put("/{søknad_uuid}/ettersend") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        withLoggingContext("søknadid" to søknadUuid.toString()) {
            validator.valider(søknadUuid, ident)
            val søknadInnsendtHendelse = SøknadInnsendtHendelse(søknadUuid, ident)
            søknadMediator.behandleSøknadInnsendtHendelse(søknadInnsendtHendelse)
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
