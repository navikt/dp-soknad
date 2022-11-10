package no.nav.dagpenger.soknad.livssyklus.start

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyInnsending
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NySøknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

internal class SøknadOpprettetHendelseMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerLogger = KotlinLogging.logger("tjenestekall.SøknadOpprettetHendelseMottak")
    }

    private val behov = listOf(NySøknad.name, NyInnsending.name)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", behov) }
            validate { it.requireKey("søknad_uuid", "ident", "@løsning") }
            validate { it.requireKey("@løsning") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val behov = packet["@behov"].single().asText()
        val søknadID = packet["søknad_uuid"].asUUID()

        withLoggingContext(
            "søknadId" to søknadID.toString()
        ) {
            val prosessversjon = mediator.prosessversjon(
                packet["@løsning"][behov]["prosessversjon"]["prosessnavn"].asText(),
                packet["@løsning"][behov]["prosessversjon"]["versjon"].asInt()
            )
            val søknadOpprettetHendelse =
                SøknadOpprettetHendelse(prosessversjon, søknadID, packet["ident"].asText())
            logger.info { "Fått løsning for '$behov' for $søknadID" }
            mediator.behandle(søknadOpprettetHendelse)
        }
    }
}

internal fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }
