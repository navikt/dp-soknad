package no.nav.dagpenger.soknad.arbeidsforhold

import no.nav.dagpenger.soknad.api.models.ArbeidsforholdResponse
import java.time.LocalDate

internal data class Arbeidsforhold(
    val id: String,
    val organisasjonsnummer: String?,
    val startdato: LocalDate,
    val sluttdato: LocalDate?,
    val stillingsprosent: Double?,
) {

    internal fun toResponse(organisasjonsnavn: String?) = ArbeidsforholdResponse(
        id = id,
        startdato = startdato,
        sluttdato = sluttdato,
        organisasjonsnavn = organisasjonsnavn,
        stillingsprosent = stillingsprosent,
    )
}
