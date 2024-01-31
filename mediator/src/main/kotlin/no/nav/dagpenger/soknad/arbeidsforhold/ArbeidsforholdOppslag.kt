package no.nav.dagpenger.soknad.arbeidsforhold

import no.nav.dagpenger.soknad.api.models.ArbeidsforholdResponse

internal class ArbeidsforholdOppslag(
    private val aaregClient: AaregClient,
    private val eregClient: EregClient,
) {

    suspend fun hentArbeidsforhold(fnr: String, subjectToken: String): List<ArbeidsforholdResponse> {
        val arbeidsforholdFraAareg = aaregClient.hentArbeidsforhold(fnr, subjectToken)

        val arbeidsforholdMedOrganisasjonsnavn = arbeidsforholdFraAareg.map {
            val organisasjonsnavn = eregClient.hentOganisasjonsnavn(it.organisasjonsnnummer)
            ArbeidsforholdResponse(
                id = it.id,
                organisasjonsnavn = organisasjonsnavn,
                startdato = it.startdato,
                sluttdato = it.sluttdato,
            )
        }.filter { it.organisasjonsnavn != null }

        return arbeidsforholdMedOrganisasjonsnavn
    }
}
