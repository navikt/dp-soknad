package no.nav.dagpenger.soknad.personalia

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.utils.auth.ident
import no.nav.dagpenger.soknad.utils.auth.jwt
import kotlin.coroutines.CoroutineContext

internal fun personaliaRouteBuilder(
    personOppslag: PersonOppslag,
    kontonummerOppslag: KontonummerOppslag,
    coroutineContext: CoroutineContext = Dispatchers.IO,
): Route.() -> Unit = { personalia(personOppslag, kontonummerOppslag, coroutineContext) }

internal fun Route.personalia(
    personOppslag: PersonOppslag,
    kontonummerOppslag: KontonummerOppslag,
    coroutineContext: CoroutineContext = Dispatchers.IO,
) {
    route("${Configuration.basePath}/personalia") {
        get {
            val fnr = call.ident()
            val jwtToken = call.request.jwt()
            val personalia =
                withContext(coroutineContext) {
                    val kontonummer = async { kontonummerOppslag.hentKontonummer(jwtToken) }
                    val person = async { personOppslag.hentPerson(fnr, jwtToken) }
                    Personalia(person.await(), kontonummer.await())
                }
            call.respond(personalia)
        }
    }
}
