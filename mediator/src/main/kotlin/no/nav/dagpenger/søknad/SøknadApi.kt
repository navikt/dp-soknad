package no.nav.dagpenger.søknad

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import no.nav.dagpenger.søknad.faktumflyt.besvarFaktumRoute
import no.nav.dagpenger.søknad.faktumflyt.nesteSøkeroppgaveRoute
import no.nav.dagpenger.søknad.livssyklus.ferdigstilling.ferdigstillSøknadRoute
import no.nav.dagpenger.søknad.livssyklus.påbegynteSøknaderRoute
import no.nav.dagpenger.søknad.livssyklus.startSøknadRoute
import no.nav.dagpenger.søknad.mal.nyesteMalRoute

internal fun søknadApiRouteBuilder(søknadMediator: SøknadMediator): Route.() -> Unit = { søknadApi(søknadMediator) }

internal fun Route.søknadApi(søknadMediator: SøknadMediator) {
    route("${Configuration.basePath}/soknad") {
        startSøknadRoute(søknadMediator)
        påbegynteSøknaderRoute(søknadMediator)
        ferdigstillSøknadRoute(søknadMediator)
        nesteSøkeroppgaveRoute(søknadMediator)
        besvarFaktumRoute(søknadMediator)
        nyesteMalRoute(søknadMediator)
    }
}
