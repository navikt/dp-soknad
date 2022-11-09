package no.nav.dagpenger.soknad.livssyklus.påbegynt

import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.dagpenger.soknad.livssyklus.asUUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

internal class MigrertSøknadMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator
) : River.PacketListener {
    private val behov = "MigrertProsess"

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
        val prosessnavn = packet["@løsning"][behov]["prosessnavn"].asText()
        val versjon = packet["@løsning"][behov]["versjon"].asInt()

        mediator.behandle(
            MigrertProsessHendelse(
                søknadId,
                ident,
                prosessversjon = Prosessversjon(prosessnavn, versjon)
            ),
            MigrertSøkerOppgave(søknadId, ident, packet)
        )
    }
}

private class MigrertSøkerOppgave(
    private val søknadId: UUID,
    private val ident: String,
    packet: JsonMessage
) :
    SøkerOppgaveMelding(packet) {
    override fun søknadUUID() = søknadId
    override fun eier() = ident
}
