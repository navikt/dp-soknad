package no.nav.dagpenger.soknad.observers

import no.nav.dagpenger.soknad.SøknadObserver
import no.nav.dagpenger.soknad.SøknadObserver.SøknadSlettetEvent
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection

internal class SøknadSlettetObserver(private val rapidsConnection: RapidsConnection) : SøknadObserver {

    override fun søknadSlettet(event: SøknadSlettetEvent) =
        rapidsConnection.publish(event.ident, søknadSlettetEvent(event))

    private fun søknadSlettetEvent(event: SøknadSlettetEvent) = JsonMessage.newMessage(
        eventName = "søknad_slettet",
        map = mapOf(
            "søknad_uuid" to event.søknadId,
            "ident" to event.ident,
        )
    ).toJson()
}
