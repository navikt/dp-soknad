package no.nav.dagpenger.quizshow.api

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.quizshow.api.søknad.FaktumSvar
import no.nav.dagpenger.quizshow.api.søknad.NySøknadMelding
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.io.Closeable

internal interface SøknadStore {
    fun håndter(faktumSvar: FaktumSvar)
    fun håndter(nySøknadMelding: NySøknadMelding)
    fun hentFakta(søknadUuid: String): String?
}

interface MeldingObserver {
    suspend fun meldingMottatt(melding: String)
}

interface Persistence : Closeable {
    fun lagre(key: String, value: String)
    fun hent(key: String): String?
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
            validate { it.demandValue("@event_name", "NySøknad") }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("fakta", "søknad_uuid", "fødselsnummer", "@opprettet") }
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
