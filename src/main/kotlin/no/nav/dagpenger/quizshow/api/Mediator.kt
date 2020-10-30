package no.nav.dagpenger.quizshow.api

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class Mediator() : River.PacketListener {
    private lateinit var rapidsConnection: RapidsConnection
    private val observers = mutableListOf<MeldingObserver>()

    init {
        River(rapidsConnection).apply {
            validate { it.demandKey("@behov") }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("seksjon", "fakta", "fødselsnummer") }
            validate { it.requireValue("seksjon.rolle", "søker") }
        }.register(this)
    }

    internal constructor(rapidsConnection: RapidsConnection) : this() {
        this.rapidsConnection = rapidsConnection
    }

    fun register(rapidsConnection: RapidsConnection) {
        this.rapidsConnection = rapidsConnection
    }

    fun register(observer: MeldingObserver) = observers.add(observer)

    fun nySøknad(fødselsnummer: String) {
        rapidsConnection.publish(ØnskerRettighetsavklaringMelding(fødselsnummer).toJson())
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) =
        observers.forEach { it.meldingMottatt(packet.toJson()) }
}

interface MeldingObserver {
    fun meldingMottatt(melding: String)
}