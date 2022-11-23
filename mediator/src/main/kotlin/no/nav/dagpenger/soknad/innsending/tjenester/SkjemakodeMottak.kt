package no.nav.dagpenger.soknad.innsending.tjenester

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.InnsendingMetadata
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingMetadataMottattHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class SkjemakodeMottak(rapidsConnection: RapidsConnection, private val mediator: InnsendingMediator) :
    River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val behov = InnsendingMetadata.name

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
        val søknadId = packet["søknad_uuid"].asUUID()
        val innsendingId = packet["innsendingId"].asUUID()
        val ident = packet["ident"].asText()

        withLoggingContext(
            "søknadId" to søknadId.toString(),
            "innsendingId" to innsendingId.toString()
        ) {
            logger.info { "Mottatt løsning for $behov for $innsendingId med skjemakode=${packet.skjemakode()}" }
            mediator.behandle(
                InnsendingMetadataMottattHendelse(
                    innsendingId = innsendingId,
                    søknadID = søknadId,
                    ident = ident,
                    skjemaKode = packet.skjemakode()
                )
            )
        }
    }

    private fun JsonMessage.skjemakode(): String = this["@løsning"][behov]["skjemakode"].asText()
}
