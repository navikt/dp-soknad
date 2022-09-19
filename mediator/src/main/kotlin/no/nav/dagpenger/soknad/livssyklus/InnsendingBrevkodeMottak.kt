package no.nav.dagpenger.soknad.livssyklus

import mu.KotlinLogging
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.InnsendingBrevkode
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class InnsendingBrevkodeMottak(rapidsConnection: RapidsConnection, mediator: SøknadMediator) : River.PacketListener {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val behov = InnsendingBrevkode.name

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", listOf(behov)) }
            validate { it.requireKey("søknad_uuid", "ident", "innsendingId", "@løsning") }
            validate {
                it.require("@løsning") { løsning ->
                    løsning.required(behov)
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        TODO("not implemented")
    }
}
