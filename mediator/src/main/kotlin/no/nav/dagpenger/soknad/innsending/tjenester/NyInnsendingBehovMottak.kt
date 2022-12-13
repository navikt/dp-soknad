package no.nav.dagpenger.soknad.innsending.tjenester

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyEttersending
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyInnsending
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.innsending.meldinger.NyInnsendingMelding
import no.nav.dagpenger.soknad.innsending.meldinger.NyInnsendingMelding.Companion.behov
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

    private val behov = listOf(NyInnsending.name, NyEttersending.name)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", behov) }
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
            val behov = packet.behov()
            logger.info { "Mottatt behov for ny innsending av type: $behov" }

            val hendelse = NyInnsendingMelding(packet).hendelse()
            mediator.behandle(hendelse)

            packet["@løsning"] = mapOf(
                behov to mapOf(
                    "innsendingId" to hendelse.innsendingId
                )
            )
            context.publish(packet.toJson())
        }
    }
}
