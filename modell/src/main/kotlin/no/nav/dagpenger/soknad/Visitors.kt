package no.nav.dagpenger.soknad

import java.util.UUID

internal interface PersonVisitor {
    fun visitPerson(søknader: List<Søknad>, ident: String)
}

internal interface SøknadVisitor {
    fun visitSøknad(søknadId: UUID, tilstand: Søknad.Tilstand)
}
