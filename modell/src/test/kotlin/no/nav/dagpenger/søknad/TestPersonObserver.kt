package no.nav.dagpenger.søknad

import no.nav.dagpenger.søknad.PersonObserver.SøknadEndretTilstandEvent

class TestPersonObserver : PersonObserver {

    internal var slettet: Boolean = false
    internal val tilstander = mutableListOf<Søknad.Tilstand.Type>().also {
        it.add(Søknad.Tilstand.Type.UnderOpprettelse)
    }

    override fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) {
        tilstander.add(event.gjeldendeTilstand)
    }

    override fun søknadSlettet(event: PersonObserver.SøknadSlettetEvent) {
        slettet = true
    }
}
