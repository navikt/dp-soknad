package no.nav.dagpenger.soknad.arbeidsforhold

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import no.nav.dagpenger.soknad.api.models.ArbeidsforholdResponse

internal class ArbeidsforholdOppslag(
    tokenProvider: (String) -> String,
    httpClient: HttpClient = HttpClient(CIO.create {}),
) {
    private val aaregClient =
        AaregClient(tokenProvider = tokenProvider, engine = httpClient.engine)

    suspend fun hentArbeidsforhold(fnr: String, subjectToken: String): List<ArbeidsforholdResponse> {
        val arbeidsforholdFraAareg = aaregClient.hentArbeidsforhold(fnr, subjectToken)

        return arbeidsforholdFraAareg.map { Arbeidsforhold.response(it) }
    }
}
