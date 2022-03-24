package no.nav.dagpenger.soknad

import java.util.UUID

internal class TestSøknadInspektør(person: Person) : PersonVisitor {

    lateinit var gjeldendetilstand: Søknad.Tilstand
    internal lateinit var personLogg: Aktivitetslogg

    init {
        person.accept(this)
    }

    override fun visitSøknad(søknadId: UUID, tilstand: Søknad.Tilstand) {
        gjeldendetilstand = tilstand
    }

    override fun postVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        personLogg = aktivitetslogg
    }
}
