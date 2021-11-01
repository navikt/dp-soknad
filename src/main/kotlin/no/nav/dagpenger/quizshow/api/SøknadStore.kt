package no.nav.dagpenger.quizshow.api

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.time.Duration

internal class SøknadStore(private val rapidsConnection: RapidsConnection) : River.PacketListener {
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
            validate { it.requireKey("fakta", "identer") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info { "Mottatt pakke ${packet["@event_name"].asText()}" }
        val fnr =
            packet["identer"].first { it["type"].asText() == "folkeregisterident" && !it["historisk"].asBoolean() }["id"].asText()
        cache.put(fnr, packet)
    }

    fun hent(fnr: String): JsonMessage? = cache.getIfPresent(fnr)

}