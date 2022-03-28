package no.nav.dagpenger.soknad

import java.util.UUID

interface TilstandVisitor {
    fun visitTilstand(tilstand: Søknad.Tilstand.Type) {}
}

interface SøknadVisitor : TilstandVisitor {
    fun visitSøknad(søknadId: UUID) {}
}

interface PersonVisitor : SøknadVisitor, AktivitetsloggVisitor {
    fun visitPerson(ident: String) {}
    fun preVisitSøknader() {}
    fun postVisitSøknader() {}
}
