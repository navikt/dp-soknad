package no.nav.dagpenger.soknad

import java.util.UUID

class Søknad(søknadId: UUID, private val tilstand: Tilstand) {

    constructor(søknadId: UUID) : this(søknadId, Opprettet)

    interface Tilstand

    object Opprettet : Tilstand

    companion object {
        internal fun harOpprettetSøknad(søknader: List<Søknad>) = søknader.any {
            it.tilstand == Opprettet
        }
    }
}
