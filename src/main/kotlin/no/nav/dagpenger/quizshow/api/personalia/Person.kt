package no.nav.dagpenger.quizshow.api.personalia

import java.time.LocalDate

interface PersonInfomasjon {
    val forNavn: String
    val mellomNavn: String
    val etterNavn: String
    val fødselsDato: LocalDate
    val postAdresse: Adresse?
    val folkeregistrertAdresse: Adresse?
}

data class Person(
    override val forNavn: String = "",
    override val mellomNavn: String = "",
    override val etterNavn: String = "",
    override val fødselsDato: LocalDate,
    override val postAdresse: Adresse?,
    override val folkeregistrertAdresse: Adresse?,
) : PersonInfomasjon
