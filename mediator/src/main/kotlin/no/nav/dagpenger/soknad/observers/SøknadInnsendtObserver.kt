package no.nav.dagpenger.soknad.observers

import no.nav.dagpenger.soknad.SøknadObserver
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection

internal class SøknadInnsendtObserver(private val rapidsConnection: RapidsConnection) : SøknadObserver {
    override fun søknadInnsendt(event: SøknadObserver.SøknadInnsendtEvent) {
        rapidsConnection.publish(søknadInnsendtEvent(event))
    }

    private fun søknadInnsendtEvent(event: SøknadObserver.SøknadInnsendtEvent) = JsonMessage.newMessage(
        eventName = "søknad_innsendt",
        map =
        mapOf(
            "søknadId" to event.søknadId,
            "søknadstidspunkt" to event.søknadTidspunkt,
            "søknadData" to event.søknadData,
            "ident" to event.ident,
        ),
    ).toJson()
}
