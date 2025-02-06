package no.nav.dagpenger.soknad.monitoring

import no.nav.dagpenger.soknad.SøknadObserver

object SøknadMetrikkObserver : SøknadObserver {
    override fun søknadTilstandEndret(event: SøknadObserver.SøknadEndretTilstandEvent) {
        Metrics.søknadTilstandTeller.labels(event.gjeldendeTilstand.name, event.forrigeTilstand.name).inc()
    }

    override fun innsendingTilstandEndret(event: SøknadObserver.SøknadInnsendingEndretTilstandEvent) {
        val innsendingEvent = event.innsending
        Metrics.innsendingTilstandTeller.labels(
            innsendingEvent.innsendingType.name,
            innsendingEvent.gjeldendeTilstand.name,
            innsendingEvent.forrigeTilstand.name,
        ).inc()
    }
}
