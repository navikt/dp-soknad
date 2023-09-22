package no.nav.dagpenger.soknad.livssyklus.påbegynt

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime

internal class SøkerOppgaveMottak(
    rapidsConnection: RapidsConnection,
    private val søknadMediator: SøknadMediator,
) : River.PacketListener {
    private companion object {
        val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "søker_oppgave") }
            validate { it.rejectKey("@løsning") }
            validate {
                it.requireKey(
                    SøkerOppgave.Keys.SEKSJONER,
                    SøkerOppgave.Keys.SØKNAD_UUID,
                    SøkerOppgave.Keys.FØDSELSNUMMER,
                    "@opprettet",
                    SøkerOppgave.Keys.FERDIG,
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søkerOppgave = SøkerOppgaveMelding(packet.toJson())
        withLoggingContext(
            "søknadId" to søkerOppgave.søknadUUID().toString(),
            "packetId" to packet.id,
            "opprettet" to packet["@opprettet"].asLocalDateTime().toString(),
        ) {
            logger.info { "Mottatt pakke ${packet["@event_name"].asText()}" }
            try {
                søknadMediator.behandle(søkerOppgave)
            } catch (e: SøknadMediator.SøknadIkkeFunnet) {
                logger.warn(e) { "Fant ikke søknad" }
            }
        }
    }
}
