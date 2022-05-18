package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person
import java.time.LocalDateTime

interface LivsyklusRepository {
    fun hent(ident: String): Person?
    fun lagre(person: Person)
    fun hentPåbegynte(personIdent: String): List<PåbegyntSøknad>
}

interface VakmesterLivsyklusRepository {
    fun slettPåbegynteSøknaderEldreEnn(tidspunkt: LocalDateTime): Int
}
