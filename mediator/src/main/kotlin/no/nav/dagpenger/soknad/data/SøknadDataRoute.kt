package no.nav.dagpenger.soknad.data

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hentSøknadUuidFraUrl
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave

internal fun søknadData(mediator: SøknadMediator): Route.() -> Unit {
    return {
        søknadData(mediator)
    }
}

internal fun Route.søknadData(søknadMediator: SøknadMediator) {
    get("${Configuration.basePath}/soknad/{søknad_uuid}/data") {
        val id = hentSøknadUuidFraUrl()
        withLoggingContext(
            "søknadId" to id.toString(),
        ) {
            val søkerOppgave: SøkerOppgave = søknadMediator.hentSøkerOppgave(id, 0)
            call.respondText(ContentType.Application.Json, HttpStatusCode.OK) { søkerOppgave.toJson() }
        }
    }
}
