package no.nav.dagpenger.soknad.arbeidsforhold

import no.nav.dagpenger.soknad.api.models.ArbeidsforholdResponse
import java.time.LocalDate

internal data class Arbeidsforhold(
    val id: String,
    val organisasjonsnnummer: String?,
    val organisasjonsnavn: String?,
    val startdato: LocalDate,
    val sluttdato: LocalDate?,
) {

    companion object {
        fun response(arbeidsforhold: Arbeidsforhold): ArbeidsforholdResponse {
            return ArbeidsforholdResponse(
                id = arbeidsforhold.id,
                organisasjonsnavn = arbeidsforhold.organisasjonsnavn ?: "",
                startdato = arbeidsforhold.startdato,
                sluttdato = arbeidsforhold.sluttdato,
            )
        }
    }
}
