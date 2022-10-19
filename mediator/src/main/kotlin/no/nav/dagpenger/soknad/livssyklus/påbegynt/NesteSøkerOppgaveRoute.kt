package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mu.withLoggingContext
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.søknadUuid
import no.nav.dagpenger.soknad.utils.auth.SøknadEierValidator
import no.nav.dagpenger.soknad.utils.auth.ident
import java.util.UUID

internal fun Route.nesteSøkeroppgaveRoute(søknadMediator: SøknadMediator) {
    val validator = SøknadEierValidator(søknadMediator)

    get("/{søknad_uuid}/neste") {
        val id = søknadUuid()
        val ident = call.ident()
        val sistLagret: Int = call.parameters["sistLagret"]?.toInt() ?: 0
        withLoggingContext(
            "søknadId" to id.toString(),
            "versjon" to sistLagret.toString()
        ) {
            validator.valider(id, ident)
            try {
                val søkerOppgave: SøkerOppgave = søknadMediator.hentSøkerOppgave(id, sistLagret)
                call.respond(HttpStatusCode.OK, søkerOppgave.asFrontendformat())
            } catch (e: NotFoundException) {
                søknadMediator.observe(object : SøknadDataRepository.SøknadDataObserver {
                    override suspend fun nyData(søkerOppgave: SøkerOppgave) {
                        if (søkerOppgave.søknadUUID() != id) return
                        call.respond(HttpStatusCode.OK, søkerOppgave.asFrontendformat())
                    }
                })
            }
            /*val søkerOppgave: SøkerOppgave = hentNesteSøkerOppgave(søknadMediator, id, sistLagret)
            call.respond(HttpStatusCode.OK, søkerOppgave.asFrontendformat())*/
        }
    }
}

private suspend fun hentNesteSøkerOppgave(søknadMediator: SøknadMediator, id: UUID, sistLagret: Int): SøkerOppgave =
    retryIO(times = 15) {
        søknadMediator.hentSøkerOppgave(id, sistLagret)
    }
