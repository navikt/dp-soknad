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
    fun håndter(rettighetsavklaringMelding: ØnskerRettighetsavklaringMelding)
    fun håndter(faktumSvar: FaktumSvar)
    fun hent(søknadUuid: String): String?
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

    override fun håndter(rettighetsavklaringMelding: ØnskerRettighetsavklaringMelding) {
        rapidsConnection.publish(rettighetsavklaringMelding.toJson())
        logger.info { "Sendte pakke ønsker_rettighetsavklaring" }
    }

    override fun håndter(faktumSvar: FaktumSvar) {
    }

    override fun hent(søknadUuid: String): String? = cache.getIfPresent(søknadUuid)

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
