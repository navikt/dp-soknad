package no.nav.dagpenger.soknad.mottak

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import no.nav.dagpenger.soknad.db.SøknadMalRepository
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class SøknadsMalMottak(rapidsConnection: RapidsConnection, private val søknadMalRepository: SøknadMalRepository) :
    River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "Søknadsmal") }
            validate { it.requireKey("versjon_id", "versjon_navn", "seksjoner") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val versjonNavn = packet["versjon_navn"].asText()
        val versjonId = packet["versjon_id"].asInt()
        søknadMalRepository.lagre(
            versjonNavn,
            versjonId,
            objectMapper.readTree(packet.toJson())
        )
        logger.info("Mottatt søknadsmal med versjon_navn $versjonNavn og versjon_id $versjonId")
    }

    private companion object {
        val objectMapper = jacksonObjectMapper()
        val logger = KotlinLogging.logger {}
    }
}
