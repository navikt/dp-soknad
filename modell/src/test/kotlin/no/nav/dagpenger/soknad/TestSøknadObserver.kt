package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.SøknadObserver.SøknadEndretTilstandEvent

class TestSøknadObserver : SøknadObserver {
    internal val tilstander = mutableListOf<Søknad.Tilstand.Type>()

    override fun tilstandEndret(søknadEndretTilstandEvent: SøknadEndretTilstandEvent) {
        tilstander.add(søknadEndretTilstandEvent.gjeldendeTilstand)
    }
}
