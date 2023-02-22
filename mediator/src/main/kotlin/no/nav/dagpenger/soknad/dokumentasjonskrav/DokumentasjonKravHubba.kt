package no.nav.dagpenger.soknad.dokumentasjonskrav

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyEttersending
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyInnsending
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class DokumentasjonKravHubba(
    rapidsConnection: RapidsConnection,
    private val dokumentkravMediator: DokumentkravMediator
) :
    River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", listOf(NyEttersending.name, NyInnsending.name)) }
            validate { it.requireKey("søknad_uuid", "ident", "innsendtTidspunkt") }
            validate { it.rejectKey("dokumentkrav", "@løsning") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadId = packet["søknad_uuid"].asUUID()

        withLoggingContext(
            "søknadId" to søknadId.toString(),
        ) {
            val aktiveDokumentKrav = dokumentkravMediator.hent(søknadId).tilDokument()

            packet["dokumentkrav"] = aktiveDokumentKrav.map { it.toMap() }
            context.publish(packet.toJson())
        }
    }
}
