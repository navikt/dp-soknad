package no.nav.dagpenger.soknad.arbeidsforhold

import java.time.LocalDate

data class Arbeidsforhold(
    val id: String,
    val organisasjonsnummer: String,
    val startdato: LocalDate,
    val sluttdato: LocalDate,
)

