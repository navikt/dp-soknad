package no.nav.dagpenger.soknad.livssyklus.start

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NySøknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadMediator.SøknadIkkeFunnet
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

internal class SøknadOpprettetHendelseMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerLogger = KotlinLogging.logger("tjenestekall.SøknadOpprettetHendelseMottak")
        private val skipBehov = listOf(
            "e8c60283-006e-439e-90d2-ffdfd533072f",
        ).map { UUID.fromString(it) }
    }

    private val behov = NySøknad.name

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", listOf(behov)) }
            validate { it.requireKey("@behovId", "søknad_uuid", "ident", "@løsning") }
            validate { it.requireKey("@løsning") }
            validate { it.requireValue("@final", true) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val behov = packet["@behov"].single().asText()
        val behovId = packet["@behovId"].asUUID()
        val søknadID = packet["søknad_uuid"].asUUID()

        if (behovId in skipBehov) return

        withLoggingContext(
            "søknadId" to søknadID.toString(),
        ) {
            val prosessversjon = try {
                mediator.prosessversjon(
                    packet["@løsning"][behov]["prosessversjon"]["prosessnavn"].asText(),
                    packet["@løsning"][behov]["prosessversjon"]["versjon"].asInt(),
                )
            } catch (e: Exception) {
                logger.warn(e) { "Kunne ikke finne prosessversjon i pakken (se sikkerlogg)" }
                sikkerLogger.warn(e) { "Kunne ikke finne prosessversjon i pakken ${packet.toJson()} " }
                throw e
            }
            val søknadOpprettetHendelse =
                SøknadOpprettetHendelse(prosessversjon, søknadID, packet["ident"].asText())
            logger.info { "Fått løsning for '$behov' for $søknadID" }
            try {
                mediator.behandle(søknadOpprettetHendelse)
            } catch (e: SøknadIkkeFunnet) {
                logger.error(e) { "Fikk svar på behov om ny søknad fra quiz, men bruker har allerede slettet søknaden" }
            }
        }
    }
}
