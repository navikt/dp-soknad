package no.nav.dagpenger.soknad.innsending.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.micrometer.core.instrument.MeterRegistry
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.InnsendingMetadata
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingMetadataMottattHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.utils.asUUID

internal class SkjemakodeMottak(rapidsConnection: RapidsConnection, private val mediator: InnsendingMediator) :
    River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val behov = InnsendingMetadata.name

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "behov")
                it.requireAllOrAny("@behov", listOf(behov))
                it.requireValue("@final", true)
            }
            validate {
                it.requireKey("søknad_uuid", "ident", "innsendingId", "@løsning")
                it.require("@løsning") { løsning ->
                    løsning.required(behov)
                }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val søknadId = packet["søknad_uuid"].asUUID()
        val innsendingId = packet["innsendingId"].asUUID()
        val ident = packet["ident"].asText()

        withLoggingContext(
            "søknadId" to søknadId.toString(),
            "innsendingId" to innsendingId.toString(),
        ) {
            logger.info { "Mottatt løsning for $behov for $innsendingId med skjemakode=${packet.skjemakode()}" }
            mediator.behandle(
                InnsendingMetadataMottattHendelse(
                    innsendingId = innsendingId,
                    ident = ident,
                    skjemaKode = packet.skjemakode(),
                ),
            )
        }
    }

    private fun JsonMessage.skjemakode(): String = this["@løsning"][behov]["skjemakode"].asText()
}
