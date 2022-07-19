package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.PersonObserver.SøknadEndretTilstandEvent

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
