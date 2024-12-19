package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadMediator.SøknadIkkeFunnet
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.dagpenger.soknad.utils.asUUID

internal class MigrertSøknadMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator,
) : River.PacketListener {
    private val skipManglendeSøknader = true
    private val behov = "MigrerProsess"

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behov")
                    it.requireAllOrAny("@behov", listOf(behov))
                }
                validate { it.requireKey("søknad_uuid", "ident", "@løsning") }
                validate {
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
        val ident = packet["ident"].asText()
        withLoggingContext(
            "søknadId" to søknadId.toString(),
        ) {
            val prosessnavn = packet["@løsning"][behov]["prosessnavn"].asText()
            val versjon = packet["@løsning"][behov]["versjon"].asInt()
            val data = packet["@løsning"][behov]["data"]

            logger.info { "Mottok migrert søknad, prosessnavn=$prosessnavn, versjon=$versjon" }

            try {
                mediator.behandle(
                    MigrertProsessHendelse(
                        søknadId,
                        ident,
                        prosessversjon = Prosessversjon(prosessnavn, versjon),
                    ),
                    SøkerOppgaveMelding(data.asText()),
                )
            } catch (e: SøknadIkkeFunnet) {
                logger.warn(e) { "Fant ikke søknad som har blitt migrert" }
                if (!skipManglendeSøknader) throw e
            }
        }
    }

    private companion object {
        private val logger = KotlinLogging.logger {}
    }
}
