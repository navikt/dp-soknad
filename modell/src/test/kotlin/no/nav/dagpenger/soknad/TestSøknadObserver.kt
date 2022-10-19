package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.SøknadObserver.SøknadEndretTilstandEvent

class TestSøknadObserver : SøknadObserver {

    internal var slettet: Boolean = false
    internal val tilstander = mutableListOf<Søknad.Tilstand.Type>().also {
        it.add(Søknad.Tilstand.Type.UnderOpprettelse)
    }

    internal val innsendTilstander = mutableListOf<Innsending.TilstandType>().also {
        it.add(Innsending.TilstandType.Opprettet)
    }

    override fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) {
        tilstander.add(event.gjeldendeTilstand)
    }

    override fun innsendingTilstandEndret(event: SøknadObserver.SøknadInnsendingEndretTilstandEvent) {
        innsendTilstander.add(event.innsending.gjeldendeTilstand)
    }

    override fun søknadSlettet(event: SøknadObserver.SøknadSlettetEvent) {
        slettet = true
    }
}
