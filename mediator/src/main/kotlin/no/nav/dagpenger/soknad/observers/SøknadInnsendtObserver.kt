package no.nav.dagpenger.soknad.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.dagpenger.soknad.SøknadObserver
import no.nav.dagpenger.soknad.utils.serder.objectMapper

internal class SøknadInnsendtObserver(
    private val rapidsConnection: RapidsConnection,
) : SøknadObserver {
    override fun søknadInnsendt(event: SøknadObserver.SøknadInnsendtEvent) {
        rapidsConnection.publish(event.ident, søknadInnsendtEvent(event))
    }

    private fun søknadInnsendtEvent(event: SøknadObserver.SøknadInnsendtEvent) =
        JsonMessage
            .newMessage(
                eventName = "søknad_innsendt_varsel",
                map =
                    mapOf(
                        "søknadId" to event.søknadId,
                        "søknadstidspunkt" to event.søknadTidspunkt,
                        "søknadData" to if (event.søknadData.isNotEmpty()) objectMapper.readTree(event.søknadData) else "",
                        "ident" to event.ident,
                    ),
            ).toJson()
}
