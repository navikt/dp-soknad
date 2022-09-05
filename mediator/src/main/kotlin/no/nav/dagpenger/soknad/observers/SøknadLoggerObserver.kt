package no.nav.dagpenger.soknad.observers

import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadObserver

object SøknadLoggerObserver : SøknadObserver {
    private val log = KotlinLogging.logger { }

    override fun søknadTilstandEndret(event: SøknadObserver.SøknadEndretTilstandEvent) {
        log.info {
            "Søknad ${event.søknadId} endret tilstand fra ${event.forrigeTilstand.name} til ${event.gjeldendeTilstand.name}"
        }
    }

    override fun søknadSlettet(event: SøknadObserver.SøknadSlettetEvent) {
        log.info {
            "Søknad ${event.søknadId} slettet"
        }
    }
}
