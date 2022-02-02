package no.nav.dagpenger.quizshow.api.personalia

import java.time.LocalDate

data class Person(
    val forNavn: String = "",
    val mellomNavn: String = "",
    val etterNavn: String = "",
    val fødselsDato: LocalDate,
    val postAdresse: Adresse?,
    val folkeregistrertAdresse: Adresse?,
)
