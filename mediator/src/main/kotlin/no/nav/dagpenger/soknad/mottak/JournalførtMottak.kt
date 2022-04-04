package no.nav.dagpenger.soknad.mottak

import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SøknadJournalførtHendelse
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
            validate { it.requireValue("@event_name", "innsending_ferdigstilt") }
            validate { it.demandValue("type", "NySøknad") }
            validate { it.requireKey("fødselsnummer") }
            validate { it.requireKey("søknadsData.søknad_uuid") }
            validate { it.requireKey("journalpostId") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadID = packet["søknadsData.søknad_uuid"].asUUID()
        val ident = packet["fødselsnummer"].asText()
        val søknadJournalførtHendelse =
            SøknadJournalførtHendelse(søknadID, ident)
        logger.info { "Fått løsning for innsending_ferdigstilt for $søknadID" }
        mediator.behandle(søknadJournalførtHendelse)
    }
}
