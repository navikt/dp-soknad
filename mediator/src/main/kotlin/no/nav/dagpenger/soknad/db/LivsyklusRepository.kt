package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person

interface LivsyklusRepository {
    fun hent(ident: String): Person?
    fun lagre(person: Person)
    fun hentPåbegynte(personIdent: String): List<PåbegyntSøknad>
}
