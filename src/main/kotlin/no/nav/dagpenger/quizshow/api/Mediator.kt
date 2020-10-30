package no.nav.dagpenger.quizshow.api

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class Mediator(private val rapidsConnection: RapidsConnection) : River.PacketListener {
    private val observers = mutableListOf<MeldingObserver>()

    init {
        River(rapidsConnection).apply {
            validate { it.demandKey("@behov") }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("root", "fakta", "fødselsnummer") }
            /*validate { it.requireValue("seksjon", "søker") }*/
        }.register(this)
    }

    fun register(observer: MeldingObserver) = observers.add(observer)

    fun nySøknad(fødselsnummer: String) {
        rapidsConnection.publish(ØnskerRettighetsavklaringMelding(fødselsnummer).toJson())
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) =
        runBlocking {
            observers.forEach { it.meldingMottatt(packet.toJson()) }
        }
}

interface MeldingObserver {
    suspend fun meldingMottatt(melding: String)
}
