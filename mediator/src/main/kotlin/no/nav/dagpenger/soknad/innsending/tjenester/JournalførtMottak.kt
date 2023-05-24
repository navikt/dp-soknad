package no.nav.dagpenger.soknad.innsending.tjenester

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.hendelse.innsending.JournalførtHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class JournalførtMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: InnsendingMediator,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerLogg = KotlinLogging.logger("tjenestekall.JournalførtMottak")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "innsending_ferdigstilt") }
            validate { it.demandAny("type", listOf("NySøknad", "Ettersending", "Gjenopptak", "Generell")) }
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
        val søknadID = packet["søknadsData"]["søknad_uuid"].asUUID()
        withLoggingContext(
            "søknadId" to søknadID.toString(),
            "journalpostId" to journalpostId,
        ) {
            val journalførtHendelse = JournalførtHendelse(ident, journalpostId)
            logger.info { "Fått løsning for innsending_ferdigstilt for $journalpostId" }
            if (journalpostId == "615962521") {
                logger.warn { "Finner ikke journalpost med id $journalpostId. Skipper behandling" }
                sikkerLogg.warn { "Finner ikke journalpost med id $journalpostId. Pakke ${packet.toJson()}" }
            } else {
                mediator.behandle(journalførtHendelse)
            }
        }
    }
}
