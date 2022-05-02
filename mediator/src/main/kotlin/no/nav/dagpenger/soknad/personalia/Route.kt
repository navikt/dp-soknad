package no.nav.dagpenger.soknad.personalia

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.auth.ident
import no.nav.dagpenger.soknad.auth.jwt
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger {}

internal fun Route.personalia(
    personOppslag: PersonOppslag,
    kontonummerOppslag: KontonummerOppslag,
    coroutineContext: CoroutineContext = Dispatchers.IO
) {

    route("${Configuration.basePath}/personalia") {
        get {
            val fnr = call.ident()
            val jwtToken = call.request.jwt()
            val personalia = withContext(coroutineContext) {
                val kontonummer = async { kontonummerOppslag.hentKontonummer(fnr) }
                val person = async { personOppslag.hentPerson(fnr, jwtToken) }
                Personalia(person.await(), kontonummer.await())
            }
            call.respond(personalia)
        }
    }
}
