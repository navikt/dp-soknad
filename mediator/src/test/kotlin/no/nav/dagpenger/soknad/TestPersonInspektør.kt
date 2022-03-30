package no.nav.dagpenger.soknad

internal class TestPersonInspektør(person: Person) : PersonVisitor {

    lateinit var gjeldendetilstand: Søknad.Tilstand.Type

    init {
        person.accept(this)
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }
}
