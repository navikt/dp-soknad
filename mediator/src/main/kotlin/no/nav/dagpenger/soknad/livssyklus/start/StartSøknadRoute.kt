package no.nav.dagpenger.soknad.livssyklus.start

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.dagpenger.soknad.Prosesstype
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.Søknadsprosess
import no.nav.dagpenger.soknad.utils.auth.ident

internal fun Route.startSøknadRoute(søknadMediator: SøknadMediator) {
    post {
        val ident = call.ident()
        val språk = call.request.queryParameters["spraak"] ?: "NB"
        val prosesstype =
            call.request.queryParameters["prosesstype"]?.let { Prosesstype.valueOf(it) } ?: Prosesstype.Søknad

        val søknadsprosess = søknadMediator.hentEllerOpprettSøknadsprosess(ident, språk, prosesstype)
        val søknadUuid = søknadsprosess.getSøknadsId()

        val statuskode = when (søknadsprosess) {
            is Søknadsprosess.NySøknadsProsess -> HttpStatusCode.Created
            is Søknadsprosess.PåbegyntSøknadsProsess -> HttpStatusCode.OK
        }

        call.response.header(HttpHeaders.Location, "${call.request.uri}/$søknadUuid/fakta")
        call.respondText(contentType = ContentType.Application.Json, statuskode) {
            søknadUuid.toString()
        }
    }
}
