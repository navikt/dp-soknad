package no.nav.dagpenger.soknad.mottak

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.serder.objectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

interface SøkerOppgave {
    fun søknadUUID(): UUID
    fun eier(): String
    fun asFrontendformat(): JsonNode
    fun asJson(): String

    object Keys {
        val SEKSJONER = "seksjoner"
        val SØKNAD_UUID = "søknad_uuid"
        val FØDSELSNUMMER = "fødselsnummer"
    }
}

internal class SøkerOppgaveMottak(
    rapidsConnection: RapidsConnection,
    private val søknadMediator: SøknadMediator
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
                    SøkerOppgave.Keys.FØDSELSNUMMER, "@opprettet"
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info { "Mottat pakke ${packet["@event_name"].asText()}" }
        søknadMediator.behandle(SøkerOppgaveMelding(packet))
    }

    internal class SøkerOppgaveMelding(private val jsonMessage: JsonMessage) : SøkerOppgave {
        override fun søknadUUID(): UUID = UUID.fromString(jsonMessage[SøkerOppgave.Keys.SØKNAD_UUID].asText())
        override fun eier(): String = jsonMessage[SøkerOppgave.Keys.FØDSELSNUMMER].asText()
        override fun asFrontendformat(): JsonNode = objectMapper.readTree(jsonMessage.toJson())
        override fun asJson(): String = jsonMessage.toJson()
    }
}
