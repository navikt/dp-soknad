package no.nav.dagpenger.soknad.arbeidsforhold

import java.time.LocalDate

data class Arbeidsforhold(
    val id: String,
    val arbeidsgiver: Arbeidsgiver,
    val endringsAarsak: String,
    val sluttAarsak: String,
    val startdato: LocalDate,
    val sluttdato: LocalDate,
    val stillingsprosent: Double,
    val antallTimerPerUke: Double,
    val ansettelsesform: String,
)

data class Arbeidsgiver(val navn: String, val land: String)
