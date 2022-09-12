package no.nav.dagpenger.soknad.livssyklus

import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class JournalførtMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "innsending_ferdigstilt") }
            validate { it.demandValue("type", "NySøknad") }
            validate { it.requireKey("fødselsnummer", "journalpostId") }
            validate {
                it.require("søknadsData") { data ->
                    data["søknad_uuid"].asUUID()
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val journalpostId = packet["journalpostId"].asText()
        val ident = packet["fødselsnummer"].asText()

        /**
         * TODO: Bør vi gjøre det sånn? 😅
         * Alternativ 1: Hente ut søknads id fra søknadsData. Kan gjøres i:
         * - dp-mottak
         * - dp-behov-journalpost
         * - eller her
         *
         * Alternativ 2: Finne søknad etter journalpost i vår database
         * Alternativ 3: La journalpost eksistere for seg selv i modellen, med en to-veis kobling mellom Journalpost og
         * Søknad. Da kan journalpost ha sin egen tilstand og søknad delegere/spørre den
         */
        val søknadsId = packet["søknadsData"]["søknad_uuid"].asUUID()
        val journalførtHendelse = JournalførtHendelse(søknadsId, journalpostId, ident)
        logger.info { "Fått løsning for innsending_ferdigstilt for $journalpostId" }
        mediator.behandle(journalførtHendelse)
    }
}
