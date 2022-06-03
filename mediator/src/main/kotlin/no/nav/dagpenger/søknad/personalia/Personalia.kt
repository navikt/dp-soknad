package no.nav.dagpenger.søknad.personalia

data class Personalia(private val person: Person, private val konto: Kontonummer) :
    KontonummerInformasjon by konto,
    PersonInfomasjon by person
