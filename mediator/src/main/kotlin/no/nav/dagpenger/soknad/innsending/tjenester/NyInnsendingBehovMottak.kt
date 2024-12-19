package no.nav.dagpenger.soknad.innsending.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyInnsending
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.innsending.meldinger.NyInnsendingMelding
import no.nav.dagpenger.soknad.utils.asUUID

internal class NyInnsendingBehovMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: InnsendingMediator,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behov")
                    it.requireAllOrAny("@behov", listOf(NyInnsending.name))
                    it.forbid("@løsning")
                }
                validate { it.requireKey("søknad_uuid", "ident", "innsendtTidspunkt") }
                validate { it.interestedIn("dokumentkrav") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val søknadId = packet["søknad_uuid"].asUUID()

        withLoggingContext(
            "søknadId" to søknadId.toString(),
        ) {
            val behov = NyInnsending.name
            logger.info { "Mottatt behov for ny innsending av type: $behov" }

            val hendelse = NyInnsendingMelding(packet).hendelse()
            mediator.behandle(hendelse)

            packet["@løsning"] =
                mapOf(
                    behov to
                        mapOf(
                            "innsendingId" to hendelse.innsendingId,
                        ),
                )
            context.publish(packet.toJson())
        }
    }
}
