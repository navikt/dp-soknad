package no.nav.dagpenger.søknad.livssyklus

import mu.KotlinLogging
import no.nav.dagpenger.søknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyJournalpost
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class NyJournalpostMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator
) : River.PacketListener {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val behov = NyJournalpost.name

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", listOf(behov)) }
            validate { it.requireKey("søknad_uuid", "ident", "@løsning") }
            validate {
                it.require("@løsning") { løsning ->
                    løsning.required(behov)
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadID = packet["søknad_uuid"].asUUID()
        val journalpostId = packet["@løsning"][behov].asText()
        val søknadMidlertidigJournalførtHendelse =
            SøknadMidlertidigJournalførtHendelse(søknadID, packet["ident"].asText(), journalpostId)
        logger.info { "Fått løsning for $behov for $søknadID med journalpostId $journalpostId" }
        mediator.behandle(søknadMidlertidigJournalførtHendelse)
    }
}
