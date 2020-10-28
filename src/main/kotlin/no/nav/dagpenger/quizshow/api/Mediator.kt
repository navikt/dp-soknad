package no.nav.dagpenger.quizshow.api

import no.nav.helse.rapids_rivers.RapidsConnection

internal class Mediator(private val rapidsConnection: RapidsConnection) {
    fun nySøknad(fødselsnummer: String) {
        rapidsConnection.publish(ØnskerRettighetsavklaringMelding(fødselsnummer).toJson())
    }

}