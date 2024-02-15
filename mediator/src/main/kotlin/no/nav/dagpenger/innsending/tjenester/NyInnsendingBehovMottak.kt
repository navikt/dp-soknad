package no.nav.dagpenger.innsending.tjenester

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.innsending.InnsendingMediator
import no.nav.dagpenger.innsending.meldinger.NyInnsendingMelding
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyInnsending
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class NyInnsendingBehovMottak(rapidsConnection: RapidsConnection, private val mediator: InnsendingMediator) :
    River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", listOf(NyInnsending.name)) }
            validate { it.requireKey("søknad_uuid", "ident", "innsendtTidspunkt") }
            validate { it.interestedIn("dokumentkrav") }
            validate { it.rejectKey("@løsning") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet["søknad_uuid"].asUUID()

        withLoggingContext(
            "søknadId" to søknadId.toString(),
        ) {
            val behov = NyInnsending.name
            logger.info { "Mottatt behov for ny innsending av type: $behov" }

            val hendelse = NyInnsendingMelding(packet).hendelse()
            mediator.behandleNyInnsendingHendelse(hendelse)

            packet["@løsning"] = mapOf(
                behov to mapOf(
                    "innsendingId" to hendelse.innsendingId,
                ),
            )
            context.publish(packet.toJson())
        }
    }
}
