package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

interface SøkerOppgave {
    fun søknadUUID(): UUID
    fun eier(): String
    fun ferdig(): Boolean
    fun asFrontendformat(): JsonNode
    fun asJson(): String

    object Keys {
        val SEKSJONER = "seksjoner"
        val SØKNAD_UUID = "søknad_uuid"
        val FØDSELSNUMMER = "fødselsnummer"
        val FERDIG = "ferdig"
    }
}

internal class SøkerOppgaveMottak(
    rapidsConnection: RapidsConnection,
    private val søknadMediator: SøknadMediator
) : River.PacketListener {
    private companion object {
        val logger = KotlinLogging.logger {}
        val sikkerLogger = KotlinLogging.logger("tjenestekall")
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
                    SøkerOppgave.Keys.FERDIG
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info { "Mottatt pakke ${packet["@event_name"].asText()}" }
        val søkerOppgave = SøkerOppgaveMelding(packet)
        withLoggingContext("søknadId" to søkerOppgave.søknadUUID().toString()) {
            søknadMediator.behandle(søkerOppgave)
        }
    }
}

internal class SøkerOppgaveMelding(private val jsonMessage: JsonMessage) : SøkerOppgave {
    override fun søknadUUID(): UUID = UUID.fromString(jsonMessage[SøkerOppgave.Keys.SØKNAD_UUID].asText())
    override fun eier(): String = jsonMessage[SøkerOppgave.Keys.FØDSELSNUMMER].asText()
    override fun ferdig(): Boolean = jsonMessage[SøkerOppgave.Keys.FERDIG].asBoolean()
    override fun asFrontendformat(): JsonNode = objectMapper.readTree(jsonMessage.toJson())
    override fun asJson(): String = jsonMessage.toJson()
}
