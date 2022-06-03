package no.nav.dagpenger.soknad.søknad

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.Søknadsprosess.NySøknadsProsess
import no.nav.dagpenger.soknad.Søknadsprosess.PåbegyntSøknadsProsess
import no.nav.dagpenger.soknad.auth.ident
import no.nav.dagpenger.soknad.søknad.faktumflyt.besvarFaktumRoute
import no.nav.dagpenger.soknad.søknad.faktumflyt.nesteSøkerOppgaveRoute
import no.nav.dagpenger.soknad.søknad.ferdigstilling.ferdigstillingRoute
import no.nav.dagpenger.soknad.søknad.mal.nyesteMalRoute

internal fun søknadApiRouteBuilder(søknadMediator: SøknadMediator): Route.() -> Unit = { søknadApi(søknadMediator) }

internal fun Route.søknadApi(søknadMediator: SøknadMediator) {
    route("${Configuration.basePath}/soknad") {
        post {
            val ident = call.ident()

            val søknadsprosess = søknadMediator.hentEllerOpprettSøknadsprosess(ident)
            val svar = søknadsprosess.getSøknadsId()

            val statuskode = when (søknadsprosess) {
                is NySøknadsProsess -> HttpStatusCode.Created
                is PåbegyntSøknadsProsess -> HttpStatusCode.OK
            }

            call.response.header(HttpHeaders.Location, "${call.request.uri}/$svar/fakta")
            call.respondText(contentType = ContentType.Application.Json, statuskode) {
                svar.toString()
            }
        }

        get("/paabegynte") {
            call.respond(HttpStatusCode.OK, søknadMediator.hentPåbegynte(call.ident()))
        }
        nesteSøkerOppgaveRoute(søknadMediator)

        besvarFaktumRoute(søknadMediator)

        ferdigstillingRoute(søknadMediator)

        nyesteMalRoute(søknadMediator)
    }
}

class IkkeTilgangExeption(melding: String) : RuntimeException(melding)
