package no.nav.dagpenger.søknad

import java.util.UUID

interface PersonObserver {
    fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) {}

    data class SøknadEndretTilstandEvent(
        val søknadId: UUID,
        val gjeldendeTilstand: Søknad.Tilstand.Type,
        val forrigeTilstand: Søknad.Tilstand.Type
    )
}
