package no.nav.dagpenger.soknad

import java.util.UUID

internal class TestPersonInspektør(person: Person) : PersonVisitor {

    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    lateinit var gjeldendeSøknadId: String

    init {
        person.accept(this)
    }

    override fun visitSøknad(søknadId: UUID) {
        gjeldendeSøknadId = søknadId.toString()
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }
}
