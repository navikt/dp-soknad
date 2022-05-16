package no.nav.dagpenger.soknad.søknad

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.soknad.serder.objectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.io.Closeable
import java.util.UUID

internal interface SøknadStore {
    fun håndter(faktumSvar: FaktumSvar)
    fun hentNesteSeksjon(søknadUuid: UUID): Søknad?
}

interface MeldingObserver {
    suspend fun meldingMottatt(melding: String)
}

interface Søknad {
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

interface Persistence : Closeable {
    fun lagre(søknad: Søknad)
    fun hent(søknadUUID: UUID): Søknad
    override fun close() {
    }
}

internal class Mediator(private val rapidsConnection: RapidsConnection, private val persistence: Persistence) :
    River.PacketListener, SøknadStore {
    private val observers = mutableListOf<MeldingObserver>()

    private companion object {
        val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "søker_oppgave") }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey(Søknad.Keys.SEKSJONER, Søknad.Keys.SØKNAD_UUID, Søknad.Keys.FØDSELSNUMMER, "@opprettet") }
        }.register(this)
    }

    fun register(observer: MeldingObserver) = observers.add(observer)

    override fun håndter(faktumSvar: FaktumSvar) {
        rapidsConnection.publish(faktumSvar.toJson())
        logger.info { "Sendte faktum svar for ${faktumSvar.søknadUuid()}" }
    }

    override fun hentNesteSeksjon(søknadUuid: UUID): Søknad = persistence.hent(søknadUuid)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info { "Mottat pakke ${packet["@event_name"].asText()}" }
        persistence.lagre(SøknadMelding(packet))
        runBlocking {
            observers.forEach { it.meldingMottatt(packet.toJson()) }
        }
    }

    private class SøknadMelding(private val jsonMessage: JsonMessage) : Søknad {
        override fun søknadUUID(): UUID = UUID.fromString(jsonMessage[Søknad.Keys.SØKNAD_UUID].asText())
        override fun eier(): String = jsonMessage[Søknad.Keys.FØDSELSNUMMER].asText()
        override fun asFrontendformat(): JsonNode = objectMapper.readTree(jsonMessage.toJson())
        override fun asJson(): String = jsonMessage.toJson()
    }
}
