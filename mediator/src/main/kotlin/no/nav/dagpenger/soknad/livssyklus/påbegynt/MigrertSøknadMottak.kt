package no.nav.dagpenger.soknad.livssyklus.påbegynt

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.dagpenger.soknad.livssyklus.asUUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class MigrertSøknadMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator
) : River.PacketListener {
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
            "søknadId" to søknadId.toString()
        ) {
            if (søknadId.toString() == "d8a4f6ef-bd07-4824-8e98-66b399e986df") return

            val prosessnavn = packet["@løsning"][behov]["prosessnavn"].asText()
            val versjon = packet["@løsning"][behov]["versjon"].asInt()
            val data = packet["@løsning"][behov]["data"]

            logger.info { "Mottok migrert søknad, prosessnavn=$prosessnavn, versjon=$versjon" }

            mediator.behandle(
                MigrertProsessHendelse(
                    søknadId,
                    ident,
                    prosessversjon = Prosessversjon(prosessnavn, versjon)
                ),
                SøkerOppgaveMelding(data.asText())
            )
        }
    }

    private companion object {
        private val logger = KotlinLogging.logger {}
    }
}
