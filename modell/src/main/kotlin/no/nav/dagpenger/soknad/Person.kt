package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Companion.harOpprettetSøknad
import java.util.UUID

class Person private constructor(private val søknader: MutableList<Søknad>, ident: String) {

    constructor(ident: String) : this(mutableListOf(), ident)

    fun opprettNySøknad() {
        if (harOpprettetSøknad(søknader)) throw IllegalStateException("Kan ikke ha flere enn én opprettet søknad.")
        søknader.add(Søknad(UUID.randomUUID()))
    }
}
