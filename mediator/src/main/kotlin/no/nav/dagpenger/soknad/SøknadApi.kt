package no.nav.dagpenger.soknad

import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.route
import no.nav.dagpenger.soknad.dokumentasjonskrav.dokumentasjonkravRoute
import no.nav.dagpenger.soknad.livssyklus.ferdigstilling.ferdigstillSøknadRoute
import no.nav.dagpenger.soknad.livssyklus.påbegynt.besvarFaktumRoute
import no.nav.dagpenger.soknad.livssyklus.påbegynt.nesteSøkeroppgaveRoute
import no.nav.dagpenger.soknad.livssyklus.påbegynt.påbegyntSøknadRoute
import no.nav.dagpenger.soknad.livssyklus.slett.slettSøknadRoute
import no.nav.dagpenger.soknad.livssyklus.start.startSøknadRoute
import no.nav.dagpenger.soknad.mal.nyesteMalRoute
import no.nav.dagpenger.soknad.minesoknader.mineSoknaderRoute
import no.nav.dagpenger.soknad.status.BehandlingsstatusClient
import no.nav.dagpenger.soknad.status.statusRoute
import java.util.UUID

internal fun søknadApiRouteBuilder(
    søknadMediator: SøknadMediator,
    behandlingsstatusClient: BehandlingsstatusClient,
): Route.() -> Unit = { søknadApi(søknadMediator, behandlingsstatusClient) }

internal fun Route.søknadApi(
    søknadMediator: SøknadMediator,
    behandlingsstatusClient: BehandlingsstatusClient,
) {
    route("${Configuration.basePath}/soknad") {
        startSøknadRoute(søknadMediator)
        påbegyntSøknadRoute(søknadMediator)
        ferdigstillSøknadRoute(søknadMediator)
        nesteSøkeroppgaveRoute(søknadMediator)
        besvarFaktumRoute(søknadMediator)
        nyesteMalRoute(søknadMediator)
        slettSøknadRoute(søknadMediator)
        statusRoute(søknadMediator, behandlingsstatusClient)
        dokumentasjonkravRoute(søknadMediator)
        mineSoknaderRoute(søknadMediator)
    }
}

internal fun RoutingContext.søknadUuid() =
    call.parameters["søknad_uuid"].let { UUID.fromString(it) }
        ?: throw IllegalArgumentException("Må ha med søknad_uuid i parameter")
