package no.nav.dagpenger.soknad.arbeidsforhold

import java.time.LocalDate

data class Arbeidsforhold(
    val id: String,
    val arbeidsgiver: Arbeidsgiver,
    val periode: Periode,
    val ansettelsesdetaljer: Ansettelsesdetaljer,
    val endringsAarsak: String,
    val sluttAarsak: String,
)

data class Arbeidsgiver(val navn: String, val land: String)

data class Periode(val startdato: LocalDate, val sluttdato: LocalDate)

data class Ansettelsesdetaljer(
    val stillingsprosent: Double,
    val antallTimerPerUke: Double,
    val ansettelsesform: String,
)
