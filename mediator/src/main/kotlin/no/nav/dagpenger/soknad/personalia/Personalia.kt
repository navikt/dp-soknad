package no.nav.dagpenger.soknad.personalia

data class Personalia(private val person: Person, private val konto: Kontonummer) :
    KontonummerInformasjon by konto,
    PersonInfomasjon by person
