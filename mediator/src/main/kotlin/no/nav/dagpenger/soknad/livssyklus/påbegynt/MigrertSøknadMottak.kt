package no.nav.dagpenger.soknad.livssyklus.påbegynt

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadMediator.SøknadIkkeFunnet
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class MigrertSøknadMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator,
) : River.PacketListener {
    private val skipManglendeSøknader = true
    private val behov = "MigrerProsess"

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", listOf(behov)) }
            validate { it.requireKey("søknad_uuid", "ident", "@løsning") }
            validate {
                it.require("@løsning") { løsning ->
                    løsning.required(behov)
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
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
