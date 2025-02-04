package no.nav.dagpenger.soknad.mal

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Prosessnavn
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.utils.serder.objectMapper

class SøknadsMalMottak(
    rapidsConnection: RapidsConnection,
    private val søknadMalRepository: SøknadMalRepository,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "Søknadsmal") }
            validate { it.requireKey("versjon_id", "versjon_navn", "seksjoner") }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
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
                    "versjon_id" to versjonId,
                ),
            )
            context.publish(nyMalMelding.toJson())
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
