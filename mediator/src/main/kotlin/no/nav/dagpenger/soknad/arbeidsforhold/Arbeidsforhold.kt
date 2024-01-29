package no.nav.dagpenger.soknad.arbeidsforhold

import java.time.LocalDate

internal data class Arbeidsforhold(
    val id: String,
    val organisasjonsnavn: String?,
    val startdato: LocalDate,
    val sluttdato: LocalDate?,
)
