package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.SøknadMediator

internal class SøkerOppgaveMottak(
    rapidsConnection: RapidsConnection,
    private val søknadMediator: SøknadMediator,
) : River.PacketListener {
    private companion object {
        val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "søker_oppgave")
                it.forbid("@løsning")
            }
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

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
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
