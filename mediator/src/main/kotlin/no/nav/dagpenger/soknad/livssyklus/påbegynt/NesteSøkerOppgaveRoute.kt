package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident
import java.time.LocalDateTime
import java.util.UUID

internal fun Route.nesteSøkeroppgaveRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)
    get("/{søknad_uuid}/neste") {
        val id = søknadUuid()
        val ident = call.ident()
        validator.valider(id, ident)
        val sistLagret = call.parameters["sistLagret"]?.let { LocalDateTime.parse(it) }
        val søkerOppgave: SøkerOppgave = hentNesteSøkerOppgave(søknadMediator, id, sistLagret)
        call.respond(HttpStatusCode.OK, søkerOppgave.asFrontendformat())
    }
}

private suspend fun hentNesteSøkerOppgave(søknadMediator: SøknadMediator, id: UUID, sistLagret: LocalDateTime?) =
    retryIO(times = 15) {
        søknadMediator.hent(id, sistLagret)
            ?: throw NotFoundException("Fant ikke søker_oppgave for søknad med id $id med sistLagret=$sistLagret")
    }
