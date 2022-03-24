package no.nav.dagpenger.soknad

import java.util.UUID

interface SøknadVisitor {
    fun visitSøknad(søknadId: UUID, tilstand: Søknad.Tilstand) {}
}

interface PersonVisitor : SøknadVisitor, AktivitetsloggVisitor {
    fun visitPerson(ident: String) {}
    fun preVisitSøknader() {}
    fun postVisitSøknader() {}
}
