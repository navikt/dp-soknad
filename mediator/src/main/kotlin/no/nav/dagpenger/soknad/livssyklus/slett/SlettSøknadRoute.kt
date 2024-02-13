package no.nav.dagpenger.soknad.livssyklus.slett

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hentSøknadUuidFraUrl
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident

private val logger = KotlinLogging.logger { }
internal fun Route.slettSøknadRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)
    delete("/{søknad_uuid}") {
        val søknadUuid = hentSøknadUuidFraUrl()
        val ident = call.ident()
        validator.valider(søknadUuid, ident)
        val slettSøknadHendelse = SlettSøknadHendelse(søknadUuid, ident)
        try {
            søknadMediator.behandleSlettSøknadHendelse(slettSøknadHendelse)
        } catch (e: SøknadMediator.SøknadIkkeFunnet) {
            logger.info(e) { "Kan ikke slette søknad som allerede er slettet" }
        }
        call.respond(HttpStatusCode.OK)
    }
}
