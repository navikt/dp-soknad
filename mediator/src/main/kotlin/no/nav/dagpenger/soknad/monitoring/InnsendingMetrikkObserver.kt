package no.nav.dagpenger.soknad.monitoring

import no.nav.dagpenger.soknad.InnsendingObserver

object InnsendingMetrikkObserver : InnsendingObserver {
    override fun innsendingTilstandEndret(event: InnsendingObserver.InnsendingEndretTilstandEvent) {
        Metrics.innsendingTilstandTeller.labels(
            event.innsendingType.name,
            event.gjeldendeTilstand.name,
            event.forrigeTilstand.name,
        ).inc()
    }
}
