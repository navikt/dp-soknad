package no.nav.dagpenger.quizshow.api.personalia

data class Personalia(private val person: Person, private val konto: Kontonummer) :
    KontonummerInformasjon by konto,
    PersonInfomasjon by person
