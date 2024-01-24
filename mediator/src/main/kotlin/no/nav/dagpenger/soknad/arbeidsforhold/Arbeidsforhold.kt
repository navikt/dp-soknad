package no.nav.dagpenger.soknad.arbeidsforhold

import java.time.LocalDate

data class Arbeidsforhold(
    val id: String,
    val arbeidsgiver: Arbeidsgiver,
    val ansettelsesdetaljer: Ansettelsesdetaljer,
    val endringsAarsak: String,
    val sluttAarsak: String,
    val startdato: LocalDate,
    val sluttdato: LocalDate,
)

data class Arbeidsgiver(val navn: String, val land: String)

data class Ansettelsesdetaljer(
    val stillingsprosent: Double,
    val antallTimerPerUke: Double,
    val ansettelsesform: String,
)
