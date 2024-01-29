package no.nav.dagpenger.soknad.arbeidsforhold

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.api.models.ArbeidsforholdResponse
import no.nav.dagpenger.soknad.utils.auth.ident
import no.nav.dagpenger.soknad.utils.auth.jwt

internal fun arbeidsforholdRouteBuilder(
    aaregClient: AaregClient,
): Route.() -> Unit = { arbeidsforhold(aaregClient) }

internal fun Route.arbeidsforhold(
    aaregClient: AaregClient,
) {
    route("${Configuration.basePath}/arbeidsforhold") {
        get {
            val fnr = call.ident()
            val jwtToken = call.request.jwt()

            val arbeidsforhold = aaregClient.hentArbeidsforhold(fnr, jwtToken)
            val arbeidsforholdResponse: List<ArbeidsforholdResponse> = arbeidsforhold.map { Arbeidsforhold.response(it) }

            call.respond(arbeidsforholdResponse)
        }
    }
}
