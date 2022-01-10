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

internal interface SøknadStore {
    fun håndter(faktumSvar: FaktumSvar)
    fun håndter(faktaMelding: FaktaMelding)
    fun hentFakta(søknadUuid: String): String?
}
interface MeldingObserver {
    suspend fun meldingMottatt(melding: String)
}

internal class Mediator(private val rapidsConnection: RapidsConnection) : River.PacketListener, SøknadStore {
    private val observers = mutableListOf<MeldingObserver>()

    private val cache: Cache<String, String> = Caffeine.newBuilder()
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

    override fun håndter(faktumSvar: FaktumSvar) {
        rapidsConnection.publish(faktumSvar.toJson())
        logger.info { "Sendte faktum svar for ${faktumSvar.søknadUuid()} ønsker_rettighetsavklaring" }
    }

    override fun håndter(faktaMelding: FaktaMelding) {
        rapidsConnection.publish(faktaMelding.toJson())
    }

    override fun hentFakta(søknadUuid: String): String? {
        TODO("Not yet implemented")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info { "Mottat pakke ${packet["@event_name"].asText()}" }
        val søknadUuid =
            packet["søknad_uuid"].asText()
        cache.put(søknadUuid, packet.toJson())
        runBlocking {
            observers.forEach { it.meldingMottatt(packet.toJson()) }
        }
    }
}
