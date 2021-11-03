package no.nav.dagpenger.quizshow.api

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.time.Duration

internal class Mediator(private val rapidsConnection: RapidsConnection) : River.PacketListener {
    private val observers = mutableListOf<MeldingObserver>()

    private val cache: Cache<String, JsonMessage> = Caffeine.newBuilder()
        .maximumSize(1000L)
        .expireAfterWrite(Duration.ofHours(24))
        .build()

    private companion object {
        val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "søker_oppgave") }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("fakta", "identer", "søknad_uuid") }
        }.register(this)
    }

    fun register(observer: MeldingObserver) = observers.add(observer)

    fun nySøknad(fødselsnummer: String) {
        rapidsConnection.publish(ØnskerRettighetsavklaringMelding(fødselsnummer).toJson())
        logger.info { "Sender pakke ønsker_rettighetsavklaring" }
    }

    fun hent(søknadUuid: String): JsonMessage? = cache.getIfPresent(søknadUuid)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info { "Mottat pakke ${packet["@event_name"].asText()}" }
        val søknadUuid =
            packet["søknad_uuid"].asText()
        cache.put(søknadUuid, packet)
        runBlocking {
            observers.forEach { it.meldingMottatt(packet.toJson()) }
        }
    }
}

interface MeldingObserver {
    suspend fun meldingMottatt(melding: String)
}
