package no.nav.dagpenger.soknad.arbeidsforhold

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.utils.auth.ident
import no.nav.dagpenger.soknad.utils.auth.jwt
import kotlin.coroutines.CoroutineContext

internal fun arbeidsforholdRouteBuilder(
    arbeidsforholdOppslag: ArbeidsforholdOppslag,
    coroutineContext: CoroutineContext = Dispatchers.IO,
): Route.() -> Unit = { arbeidsforhold(arbeidsforholdOppslag, coroutineContext) }

internal fun Route.arbeidsforhold(
    arbeidsforholdOppslag: ArbeidsforholdOppslag,
    coroutineContext: CoroutineContext = Dispatchers.IO,
) {
    route("${Configuration.basePath}/arbeidsforhold") {
        get {
            val fnr = call.ident()
            val jwtToken = call.request.jwt()

            val arbeidsforhold = withContext(coroutineContext) {
                arbeidsforholdOppslag.hentArbeidsforhold(fnr, jwtToken)
            }

            call.respond(arbeidsforhold)
        }
    }
}
