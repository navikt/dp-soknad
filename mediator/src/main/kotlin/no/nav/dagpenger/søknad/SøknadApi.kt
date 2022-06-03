package no.nav.dagpenger.søknad

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import no.nav.dagpenger.søknad.faktumflyt.besvarFaktumRoute
import no.nav.dagpenger.søknad.faktumflyt.nesteSøkerOppgaveRoute
import no.nav.dagpenger.søknad.livssyklus.ferdigstilling.ferdigstillingRoute
import no.nav.dagpenger.søknad.livssyklus.påbegynteRoute
import no.nav.dagpenger.søknad.livssyklus.startSøknadRoute
import no.nav.dagpenger.søknad.mal.nyesteMalRoute

internal fun søknadApiRouteBuilder(søknadMediator: SøknadMediator): Route.() -> Unit = { søknadApi(søknadMediator) }

internal fun Route.søknadApi(søknadMediator: SøknadMediator) {
    route("${Configuration.basePath}/soknad") {
        startSøknadRoute(søknadMediator)
        påbegynteRoute(søknadMediator)
        nesteSøkerOppgaveRoute(søknadMediator)
        besvarFaktumRoute(søknadMediator)
        ferdigstillingRoute(søknadMediator)
        nyesteMalRoute(søknadMediator)
    }
}

class IkkeTilgangExeption(melding: String) : RuntimeException(melding)
