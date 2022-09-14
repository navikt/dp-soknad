package no.nav.dagpenger.soknad.livssyklus

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.ArkiverbarSøknad
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Journalpost.Variant
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

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

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadID = packet["søknad_uuid"].asUUID()
        val arkiverbarSøknadMottattHendelse =
            ArkiverbarSøknadMottattHendelse(
                søknadID,
                packet["ident"].asText(),
                Søknad.Journalpost(varianter = packet["@løsning"][behov].dokumentVarianter()),
                arkiverbartDokument = packet["@løsning"][behov].dokumentVarianter()
            )
        logger.info { "Fått løsning for $behov for $søknadID" }
        mediator.behandle(arkiverbarSøknadMottattHendelse)
    }

    private fun JsonNode.dokumentVarianter(): List<Variant> = this.toList().map { node ->
        val format = when (node["metainfo"]["variant"].asText()) {
            "NETTO" -> "ARKIV"
            "BRUTTO" -> "FULLVERSJON"
            else -> {
                throw IllegalArgumentException("Ukjent joarkvariant, se https://confluence.adeo.no/display/BOA/Variantformat")
            }
        }
        Variant(
            urn = node["urn"].asText(),
            format = format,
            type = node["metainfo"]["filtype"].asText()
        )
    }
}

internal fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }
