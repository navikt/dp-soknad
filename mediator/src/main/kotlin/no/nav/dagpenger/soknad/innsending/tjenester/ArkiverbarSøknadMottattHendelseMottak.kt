package no.nav.dagpenger.soknad.innsending.tjenester

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.ArkiverbarSøknad
import no.nav.dagpenger.soknad.Innsending.Dokument.Dokumentvariant
import no.nav.dagpenger.soknad.hendelse.innsending.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class ArkiverbarSøknadMottattHendelseMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: InnsendingMediator,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val behov = ArkiverbarSøknad.name

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", listOf(behov)) }
            validate { it.requireKey("søknad_uuid", "ident", "innsendingId", "@løsning") }
            validate {
                it.require("@løsning") { løsning ->
                    løsning.required(behov)
                }
            }
            validate { it.requireValue("@final", true) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadID = packet["søknad_uuid"].asUUID()
        val innsendingId = packet["innsendingId"].asUUID()
        withLoggingContext(
            "søknadId" to søknadID.toString(),
            "innsendingId" to innsendingId.toString(),
        ) {
            val arkiverbarSøknadMottattHendelse = ArkiverbarSøknadMottattHendelse(
                innsendingId = innsendingId,
                ident = packet["ident"].asText(),
                dokumentvarianter = packet["@løsning"][behov].dokumentVarianter(),
            )
            logger.info { "Fått løsning for $behov for $innsendingId" }
            mediator.behandle(arkiverbarSøknadMottattHendelse)
        }
    }

    private fun JsonNode.dokumentVarianter(): List<Dokumentvariant> = this.toList().map { node ->
        val format = when (node["metainfo"]["variant"].asText()) {
            "NETTO" -> "ARKIV"
            "BRUTTO" -> "FULLVERSJON"
            else -> {
                throw IllegalArgumentException("Ukjent joarkvariant, se https://confluence.adeo.no/display/BOA/Variantformat")
            }
        }
        Dokumentvariant(
            filnavn = node["metainfo"]["innhold"].asText(),
            urn = node["urn"].asText(),
            variant = format,
            type = node["metainfo"]["filtype"].asText(),
        )
    }
}
