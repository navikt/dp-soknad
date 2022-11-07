package no.nav.dagpenger.soknad.mal

import mu.KotlinLogging
import no.nav.dagpenger.soknad.Prosessnavn
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

class SøknadsMalMottak(
    rapidsConnection: RapidsConnection,
    private val søknadMalRepository: SøknadMalRepository
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "Søknadsmal") }
            validate { it.requireKey("versjon_id", "versjon_navn", "seksjoner") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val versjonNavn = packet["versjon_navn"].asText()
        val versjonId = packet["versjon_id"].asInt()
        val prosessversjon = Prosessversjon(Prosessnavn(versjonNavn), versjonId)
        val søknadMal = SøknadMal(prosessversjon, objectMapper.readTree(packet.toJson()))

        if (søknadMalRepository.lagre(søknadMal) == 1) {
            logger.info("Mottatt søknadsmal med versjon_navn $versjonNavn og versjon_id $versjonId")
            val nyMalMelding = JsonMessage.newMessage(
                eventName = "ny_quiz_mal",
                map = mapOf(
                    "versjon_navn" to versjonNavn,
                    "versjon_id" to versjonId
                )
            )
            context.publish(nyMalMelding.toJson())
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
