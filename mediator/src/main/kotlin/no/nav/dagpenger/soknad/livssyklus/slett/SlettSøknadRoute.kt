package no.nav.dagpenger.soknad.livssyklus.slett

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.ident

internal fun Route.slettSøknadRoute(søknadMediator: SøknadMediator) {
    delete("/{søknad_uuid}") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        val slettSøknadHendelse = SlettSøknadHendelse(søknadUuid, ident)
        søknadMediator.behandle(slettSøknadHendelse)
        call.respond(HttpStatusCode.OK)
    }
}
