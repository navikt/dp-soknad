package no.nav.dagpenger.søknad.observers

import mu.KotlinLogging
import no.nav.dagpenger.søknad.PersonObserver

object PersonLoggerObserver : PersonObserver {
    private val log = KotlinLogging.logger { }

    override fun søknadTilstandEndret(event: PersonObserver.SøknadEndretTilstandEvent) {
        log.info {
            "Søknad ${event.søknadId} endret tilstand fra ${event.forrigeTilstand.name} til ${event.gjeldendeTilstand.name}"
        }
    }
}
