package no.nav.dagpenger.søknad.observers

import no.nav.dagpenger.søknad.PersonObserver
import no.nav.dagpenger.søknad.PersonObserver.SøknadSlettetEvent
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import java.util.UUID

internal class SøknadSlettetObserver(private val rapidsConnection: RapidsConnection) : PersonObserver {

    override fun søknadSlettet(event: SøknadSlettetEvent) =
        rapidsConnection.publish(event.ident, søknadSlettetEvent(event.søknadId))

    private fun søknadSlettetEvent(søknadUuid: UUID) = JsonMessage.newMessage(
        eventName = "søknad_slettet",
        map = mapOf(
            "søknad_uuid" to søknadUuid,
        )
    ).toJson()
}
