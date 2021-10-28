package no.nav.dagpenger.quizshow.api

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class Mediator(private val rapidsConnection: RapidsConnection) : River.PacketListener {
    private val observers = mutableListOf<MeldingObserver>()

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

    fun register(observer: MeldingObserver) = observers.add(observer)

    fun nySøknad(fødselsnummer: String) {
        rapidsConnection.publish(ØnskerRettighetsavklaringMelding(fødselsnummer).toJson())
        logger.info { "Sender pakke ønsker_rettighetsavklaring" }
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info { "Mottat pakke ${packet["@event_name"].asText()}" }
        runBlocking {
            observers.forEach { it.meldingMottatt(packet.toJson()) }
        }
    }
}

interface MeldingObserver {
    suspend fun meldingMottatt(melding: String)
}
