package no.nav.dagpenger.soknad.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.dagpenger.soknad.DokumentkravObserver
import no.nav.dagpenger.soknad.SøknadObserver
import no.nav.dagpenger.soknad.SøknadObserver.SøknadEndretTilstandEvent
import no.nav.dagpenger.soknad.SøknadObserver.SøknadSlettetEvent

internal class SøknadTilstandObserver(private val rapidsConnection: RapidsConnection) : SøknadObserver {
    override fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) =
        rapidsConnection.publish(event.ident, søknadTilstandEndretEvent(event))

    private fun søknadTilstandEndretEvent(event: SøknadEndretTilstandEvent) =
        JsonMessage.newMessage(
            "søknad_endret_tilstand",
            listOfNotNull(
                "søknad_uuid" to event.søknadId,
                "ident" to event.ident,
                "forrigeTilstand" to event.forrigeTilstand,
                "gjeldendeTilstand" to event.gjeldendeTilstand,
                event.prosessversjon?.prosessnavn?.let { "prosessnavn" to it.id },
            ).toMap(),
        ).toJson()

    override fun søknadSlettet(event: SøknadSlettetEvent) = rapidsConnection.publish(event.ident, søknadSlettetEvent(event))

    private fun søknadSlettetEvent(event: SøknadSlettetEvent) =
        JsonMessage.newMessage(
            eventName = "søknad_slettet",
            map =
                mapOf(
                    "søknad_uuid" to event.søknadId,
                    "ident" to event.ident,
                ),
        ).toJson()

    override fun dokumentkravInnsendt(event: DokumentkravObserver.DokumentkravInnsendtEvent) =
        rapidsConnection.publish(event.ident, dokumentkravInnsendtEvent(event))

    private fun dokumentkravInnsendtEvent(event: DokumentkravObserver.DokumentkravInnsendtEvent) =
        JsonMessage.newMessage(
            eventName = "dokumentkrav_innsendt",
            map =
                mapOf(
                    "søknad_uuid" to event.søknadId,
                    "ident" to event.ident,
                    "søknadType" to event.søknadType!!,
                    "innsendingsType" to event.innsendingstype!!,
                    "innsendttidspunkt" to event.innsendttidspunkt,
                    "ferdigBesvart" to event.ferdigBesvart,
                    "hendelseId" to event.hendelseId,
                    "dokumentkrav" to
                        event.dokumentkrav.map {
                            mapOf(
                                "dokumentnavn" to it.dokumentnavn,
                                "skjemakode" to it.skjemakode,
                                "valg" to it.valg,
                            )
                        },
                ),
        ).toJson()
}
