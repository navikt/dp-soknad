package no.nav.dagpenger.søknad.livssyklus.slett

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.søknad.søknadUuid
import no.nav.dagpenger.søknad.utils.auth.ident

internal fun Route.slettSøknadRoute(søknadMediator: SøknadMediator) {
    delete("/{søknad_uuid}") {
        val søknadUuid = søknadUuid()
        val ident = call.ident()
        val slettSøknadHendelse = SlettSøknadHendelse(søknadUuid, ident)
        søknadMediator.behandle(slettSøknadHendelse)
        call.respond(HttpStatusCode.OK)
    }
}
