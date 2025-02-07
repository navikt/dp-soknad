package no.nav.dagpenger.soknad.innsending.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.hendelse.innsending.JournalførtHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.utils.asUUID

internal class JournalførtMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: InnsendingMediator,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "innsending_ferdigstilt")
                it.requireAny("type", listOf("NySøknad", "Ettersending", "Gjenopptak", "Generell"))
            }
            validate {
                it.requireKey("fødselsnummer", "journalpostId")
                it.require("søknadsData") { data ->
                    data["søknad_uuid"].asUUID()
                }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
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
            mediator.behandle(journalførtHendelse)
        }
    }
}
