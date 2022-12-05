package no.nav.dagpenger.soknad.observers

import no.nav.dagpenger.soknad.SøknadObserver
import no.nav.dagpenger.soknad.SøknadObserver.SøknadEndretTilstandEvent
import no.nav.dagpenger.soknad.SøknadObserver.SøknadSlettetEvent
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection

internal class SøknadTilstandObserver(private val rapidsConnection: RapidsConnection) : SøknadObserver {
    override fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) =
        rapidsConnection.publish(event.ident, søknadTilstandEndretEvent(event))

    private fun søknadTilstandEndretEvent(event: SøknadEndretTilstandEvent) = JsonMessage.newMessage(
        "søknad_endret_tilstand",
        mapOf(
            "søknad_uuid" to event.søknadId,
            "ident" to event.ident,
            "forrigeTilstand" to event.forrigeTilstand,
            "gjeldendeTilstand" to event.gjeldendeTilstand
        )
    ).toJson()

    override fun søknadSlettet(event: SøknadSlettetEvent) =
        rapidsConnection.publish(event.ident, søknadSlettetEvent(event))

    private fun søknadSlettetEvent(event: SøknadSlettetEvent) = JsonMessage.newMessage(
        eventName = "søknad_slettet",
        map = mapOf(
            "søknad_uuid" to event.søknadId,
            "ident" to event.ident
        )
    ).toJson()
}
