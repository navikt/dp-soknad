package no.nav.dagpenger.soknad.innsending.tjenester

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyJournalpost
import no.nav.dagpenger.soknad.hendelse.innsending.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class NyJournalpostMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: InnsendingMediator
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val behov = NyJournalpost.name

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", listOf(behov)) }
            validate { it.requireKey("søknad_uuid", "ident", "innsendingId", "@løsning") }
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
        val innsendingId = packet["innsendingId"].asUUID()

        withLoggingContext(
            "søknadId" to søknadID.toString(),
            "innsendingId" to innsendingId.toString()
        ) {
            val søknadMidlertidigJournalførtHendelse =
                SøknadMidlertidigJournalførtHendelse(
                    innsendingId,
                    packet["ident"].asText(),
                    journalpostId
                )
            logger.info { "Fått løsning for $behov for $innsendingId med journalpostId $journalpostId" }
            mediator.behandle(søknadMidlertidigJournalførtHendelse)
        }
    }
}
