package no.nav.dagpenger.søknad

import no.nav.dagpenger.søknad.PersonObserver.SøknadEndretTilstandEvent

class TestPersonObserver : PersonObserver {
    internal val tilstander = mutableListOf<Søknad.Tilstand.Type>().also {
        it.add(Søknad.Tilstand.Type.UnderOpprettelse)
    }

    override fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) {
        tilstander.add(event.gjeldendeTilstand)
    }
}
