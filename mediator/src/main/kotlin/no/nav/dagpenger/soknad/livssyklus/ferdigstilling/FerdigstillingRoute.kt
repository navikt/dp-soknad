package no.nav.dagpenger.soknad.livssyklus.ferdigstilling

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put
import mu.withLoggingContext
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident

internal fun Route.ferdigstillSøknadRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)
    put("/{søknad_uuid}/ferdigstill") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        withLoggingContext("søknadid" to søknadUuid.toString()) {
            validator.valider(søknadUuid, ident)
            val søknadInnsendtHendelse = SøknadInnsendtHendelse(søknadUuid, ident)
            call.receive<JsonNode>().let {
                søknadMediator.lagreSøknadsTekst(søknadUuid, it.toString())
            }
            søknadMediator.behandle(søknadInnsendtHendelse)
        }
        call.respond(HttpStatusCode.NoContent)
    }

    put("/{søknad_uuid}/ettersend") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        withLoggingContext("søknadid" to søknadUuid.toString()) {
            validator.valider(søknadUuid, ident)
            val søknadInnsendtHendelse = SøknadInnsendtHendelse(søknadUuid, ident)
            søknadMediator.behandle(søknadInnsendtHendelse)
        }
        call.respond(HttpStatusCode.NoContent)
    }
}
