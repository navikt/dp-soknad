package no.nav.dagpenger.soknad.observers

import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadObserver

object SøknadLoggerObserver : SøknadObserver {
    private val log = KotlinLogging.logger { }
    private val sikkerlogg = KotlinLogging.logger { "tjenestekall.SøknadLoggerObserver" }

    override fun søknadTilstandEndret(event: SøknadObserver.SøknadEndretTilstandEvent) {
        log.info {
            "Søknad ${event.søknadId} endret tilstand fra ${event.forrigeTilstand.name} til ${event.gjeldendeTilstand.name}"
        }
    }

    override fun innsendingTilstandEndret(event: SøknadObserver.SøknadInnsendingEndretTilstandEvent) {
        log.info {
            "Søknad ${event.søknadId} med innsending ${event.innsending.innsendingId} av type ${event.innsending.innsendingType.name}\n " +
                "endret tilstand fra ${event.innsending.forrigeTilstand.name} til ${event.innsending.gjeldendeTilstand.name}"
        }
    }

    override fun søknadSlettet(event: SøknadObserver.SøknadSlettetEvent) {
        log.info {
            "Søknad ${event.søknadId} slettet"
        }
    }

    override fun søknadInnsendt(event: SøknadObserver.SøknadInnsendtEvent) {
        sikkerlogg.info {
            "Søknad ${event.søknadId} innsendt på tidspunkt ${event.søknadTidspunkt} med søknaddata ${event.søknadData}"
        }
    }
}
