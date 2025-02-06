package no.nav.dagpenger.soknad.arbeidsforhold

import no.nav.dagpenger.soknad.api.models.ArbeidsforholdResponse

internal class ArbeidsforholdOppslag(
    private val aaregClient: AaregClient,
    private val eregClient: EregClient,
) {
    suspend fun hentArbeidsforhold(
        fnr: String,
        subjectToken: String,
    ): List<ArbeidsforholdResponse> {
        val arbeidsforholdFraAareg = aaregClient.hentArbeidsforhold(fnr, subjectToken)

        val arbeidsforholdResponses =
            arbeidsforholdFraAareg.map {
                val organisasjonsnavn = eregClient.hentOganisasjonsnavn(it.organisasjonsnummer)
                it.toResponse(organisasjonsnavn)
            }

        val fantOrgnavnForAlleArbeidsforhold = arbeidsforholdResponses.none { it.organisasjonsnavn == null }

        return if (fantOrgnavnForAlleArbeidsforhold) arbeidsforholdResponses else emptyList()
    }
}
