package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.PersonObserver.SøknadEndretTilstandEvent

class TestPersonObserver : PersonObserver {
    internal val tilstander = mutableListOf<Søknad.Tilstand.Type>().also {
        it.add(Søknad.Tilstand.Type.UnderOpprettelse)
    }

    override fun søknadTilstandEndret(søknadEndretTilstandEvent: SøknadEndretTilstandEvent) {
        tilstander.add(søknadEndretTilstandEvent.gjeldendeTilstand)
    }
}
