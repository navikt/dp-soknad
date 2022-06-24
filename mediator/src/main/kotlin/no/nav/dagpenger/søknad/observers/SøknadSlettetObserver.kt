package no.nav.dagpenger.søknad.observers

import no.nav.dagpenger.søknad.PersonObserver
import no.nav.dagpenger.søknad.PersonObserver.SøknadSlettetEvent
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.LocalDateTime
import java.util.UUID

class SøknadSlettetObserver(private val rapidsConnection: RapidsConnection) : PersonObserver {

    override fun søknadSlettet(event: SøknadSlettetEvent) =
        rapidsConnection.publish(event.ident, søknadSlettetEvent(event.søknadId))

    private fun søknadSlettetEvent(søknadUuid: UUID) = JsonMessage.newMessage(
        mapOf(
            "@event_name" to "søknad_slettet",
            "@opprettet" to LocalDateTime.now(),
            "@id" to "${UUID.randomUUID()}",
            "søknad_uuid" to søknadUuid,
        )
    ).toJson()
}
