package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person

interface PersonRepository {
    fun hent(ident: String): Person?
    fun lagre(person: Person)
}
