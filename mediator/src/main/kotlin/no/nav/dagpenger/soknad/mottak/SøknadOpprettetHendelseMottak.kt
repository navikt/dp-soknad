package no.nav.dagpenger.soknad.mottak

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NySøknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

internal class SøknadOpprettetHendelseMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator
) : River.PacketListener {

    companion object {
        private val logger = KotlinLogging.logger {}
        val sikkerLogger = KotlinLogging.logger("tjenestekall.SøknadOpprettetHendelseMottak")
    }

    private val behov = NySøknad.name

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name", "behov") }
            validate { it.requireAllOrAny("@behov", listOf(behov)) }
            validate { it.requireKey("søknad_uuid", "ident", "@løsning") }
            validate {
                it.require("@løsning") { løsning ->
                    løsning.required(behov)
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadID = packet["@løsning"][behov].asUUID()
        val søknadOpprettetHendelse =
            SøknadOpprettetHendelse(søknadID, packet["ident"].asText())
        logger.info { "Fått løsning for $behov for $søknadID" }
        mediator.behandle(søknadOpprettetHendelse)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logger.error { problems.toString() }
        sikkerLogger.error { problems.toExtendedReport() }
    }
}

internal fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }
