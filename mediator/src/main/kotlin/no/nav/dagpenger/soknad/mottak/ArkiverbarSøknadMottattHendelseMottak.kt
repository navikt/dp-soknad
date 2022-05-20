package no.nav.dagpenger.soknad.mottak

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.ArkiverbarSøknad
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Dokument.Variant
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class ArkiverbarSøknadMottattHendelseMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator
) : River.PacketListener {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val behov = ArkiverbarSøknad.name

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

    private fun JsonNode.dokumentVarianter(): List<Variant> = this.toList().map { node ->
        Variant(
            urn = node["urn"].asText(),
            format = "ARKIV",
            type = node["metainfo"]["filtype"].asText()
        )
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadID = packet["søknad_uuid"].asUUID()
        val dokumentVarianter: List<Variant> = packet["@løsning"][behov].dokumentVarianter()
        val arkiverbarSøknadMottattHendelse =
            ArkiverbarSøknadMottattHendelse(
                søknadID,
                packet["ident"].asText(),
                Søknad.Dokument(varianter = dokumentVarianter)
            ) // todo
        logger.info { "Fått løsning for $behov for $søknadID" }
        mediator.behandle(arkiverbarSøknadMottattHendelse)
    }
}
