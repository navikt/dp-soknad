package no.nav.dagpenger.soknad.livssyklus

import mu.KotlinLogging
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.InnsendingBrevkode
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.BrevkodeMottattHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class InnsendingBrevkodeMottak(rapidsConnection: RapidsConnection, private val mediator: SøknadMediator) :
    River.PacketListener {

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
        val søknadID = packet["søknad_uuid"].asUUID()
        val innsendingId = packet["innsendingId"].asUUID()
        val ident = packet["ident"].asText()
        mediator.behandle(
            BrevkodeMottattHendelse(
                innsendingId = innsendingId,
                søknadID = søknadID,
                ident = ident,
                tittel = packet.tittel(),
                skjemaKode = packet.skjemakode()
            )
        )
    }

    private fun JsonMessage.tittel(): String = this["@løsning"][behov]["tittel"].asText()
    private fun JsonMessage.skjemakode(): String = this["@løsning"][behov]["skjemakode"].asText()
}