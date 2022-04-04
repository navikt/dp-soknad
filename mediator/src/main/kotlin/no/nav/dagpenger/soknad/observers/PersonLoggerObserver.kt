package no.nav.dagpenger.soknad.observers

import mu.KotlinLogging
import no.nav.dagpenger.soknad.PersonObserver

object PersonLoggerObserver : PersonObserver {
    private val log = KotlinLogging.logger { }

    override fun søknadTilstandEndret(event: PersonObserver.SøknadEndretTilstandEvent) {
        log.info {
            "Søknad ${event.søknadId} endret tilstand fra ${event.forrigeTilstand.name} til ${event.gjeldendeTilstand.name}"
        }
    }
}
