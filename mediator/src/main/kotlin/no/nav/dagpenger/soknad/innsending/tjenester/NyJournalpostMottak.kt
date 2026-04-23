package no.nav.dagpenger.soknad.innsending.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.micrometer.core.instrument.MeterRegistry
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyJournalpost
import no.nav.dagpenger.soknad.hendelse.innsending.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.utils.asUUID

internal class NyJournalpostMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: InnsendingMediator,
) : River.PacketListener {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val behov = NyJournalpost.name

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "behov")
                it.requireAllOrAny("@behov", listOf(behov))
                it.requireValue("@final", true)
            }
            validate {
                it.requireKey("søknad_uuid", "ident", "innsendingId", "@løsning")
                it.require("@løsning") { løsning ->
                    løsning.required(behov)
                }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val søknadID = packet["søknad_uuid"].asUUID()
        val journalpostId = packet["@løsning"][behov].asText()
        val innsendingId = packet["innsendingId"].asUUID()

        withLoggingContext(
            "søknadId" to søknadID.toString(),
            "innsendingId" to innsendingId.toString(),
        ) {
            val søknadMidlertidigJournalførtHendelse =
                SøknadMidlertidigJournalførtHendelse(
                    innsendingId,
                    packet["ident"].asText(),
                    journalpostId,
                )
            logger.info { "Fått løsning for $behov for $innsendingId med journalpostId $journalpostId" }
            mediator.behandle(søknadMidlertidigJournalførtHendelse)
        }
    }
}
