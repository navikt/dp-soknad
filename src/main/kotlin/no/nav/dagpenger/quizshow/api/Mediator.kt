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
    fun håndter(nySøknadMelding: NySøknadMelding)
    fun hentFakta(søknadUuid: String): String?
}

interface MeldingObserver {
    suspend fun meldingMottatt(melding: String)
}

interface Persistence {
    fun lagre(key: String, value: String)
    fun hent(key: String): String?
}

internal class Mediator(private val rapidsConnection: RapidsConnection, private val persistence: Persistence = cache) :
    River.PacketListener, SøknadStore {
    private val observers = mutableListOf<MeldingObserver>()

    private companion object {
        val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall")

        private val cache: Persistence = object : Persistence {
            private val caffeineCache: Cache<String, String> = Caffeine.newBuilder()
                .maximumSize(1000L)
                .expireAfterWrite(Duration.ofHours(24))
                .build()

            override fun lagre(key: String, value: String) {
                caffeineCache.put(key, value)
            }

            override fun hent(key: String): String? {
                return caffeineCache.getIfPresent(key)
            }
        }
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "NySøknad") }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("fakta", "søknad_uuid", "fødselsnummer", "opprettet") }
        }.register(this)
    }

    fun register(observer: MeldingObserver) = observers.add(observer)

    override fun håndter(faktumSvar: FaktumSvar) {
        rapidsConnection.publish(faktumSvar.toJson())
        logger.info { "Sendte faktum svar for ${faktumSvar.søknadUuid()} ønsker_rettighetsavklaring" }
    }

    override fun håndter(nySøknadMelding: NySøknadMelding) {
        rapidsConnection.publish(
            nySøknadMelding.toJson().also {
                sikkerlogg.info { "Nysøknad: $it" }
            }
        )
    }

    override fun hentFakta(søknadUuid: String): String? = persistence.hent(søknadUuid)

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info { "Mottat pakke ${packet["@event_name"].asText()}" }
        val søknadUuid =
            packet["søknad_uuid"].asText()
        persistence.lagre(søknadUuid, packet.toJson())
        runBlocking {
            observers.forEach { it.meldingMottatt(packet.toJson()) }
        }
    }
}
