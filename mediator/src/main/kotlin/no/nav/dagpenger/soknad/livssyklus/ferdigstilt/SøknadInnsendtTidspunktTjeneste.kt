package no.nav.dagpenger.soknad.livssyklus.ferdigstilt

import de.slub.urn.URN
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadVisitor
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.rapids_rivers.withMDC
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

internal class SøknadInnsendtTidspunktTjeneste(
    rapidsConnection: RapidsConnection,
    private val mediator: SøknadMediator,
) : River.PacketListener {

    private companion object {
        val logger = KotlinLogging.logger { }
        val sikkerlogg = KotlinLogging.logger("tjenestekall.SøknadInnsendtTidspunkt")
    }

    private val behov = "Søknadstidspunkt"

    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("@event_name", listOf("faktum_svar", "behov")) }
            validate { it.requireContains("@behov", behov) }
            validate { it.rejectKey("@løsning") }
            validate { it.interestedIn("InnsendtSøknadsId", "Søknadstidspunkt.søknad_uuid") }
            validate { it.interestedIn("søknad_uuid", "@behovId") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        withMDC(
            mapOf(
                "søknad_uuid" to packet["søknad_uuid"].asText(),
                "behovId" to packet["@behovId"].asText(),
            ),
        ) {
            try {
                val innsendtSøknadsId = packet.getSøknadIdForRapporteringBehov() ?: packet.getSøknadIdForQuizBehov()
                val innsendtTidspunkt: LocalDate? = mediator.hent(UUID.fromString(innsendtSøknadsId))?.let {
                    object : SøknadVisitor {
                        var innsendt: LocalDate? = null

                        init {
                            it.accept(this)
                        }

                        override fun visitSøknad(
                            søknadId: UUID,
                            ident: String,
                            opprettet: ZonedDateTime,
                            innsendt: ZonedDateTime?,
                            tilstand: Søknad.Tilstand,
                            språk: Språk,
                            dokumentkrav: Dokumentkrav,
                            sistEndretAvBruker: ZonedDateTime,
                            prosessversjon: Prosessversjon?,
                        ) {
                            this.innsendt = innsendt?.toLocalDate()
                        }
                    }.innsendt
                }

                if (innsendtTidspunkt != null) {
                    packet["@løsning"] = mapOf(behov to innsendtTidspunkt)
                    context.publish(packet.toJson())
                    logger.info { "Løst behov $behov for søknad: $innsendtSøknadsId" }
                } else {
                    logger.error { "Fant ikke $behov for søknad: $innsendtSøknadsId" }
                    sikkerlogg.error { "Fant ikke $behov for søknad: $innsendtSøknadsId. \n packet: ${packet.toJson()}" }
                }
            } catch (e: Exception) {
                sikkerlogg.error(e) { "feil ved behov $behov. \n packet: ${packet.toJson()}" }
            }
        }
    }
}

private fun JsonMessage.getSøknadIdForQuizBehov(): String {
    return this["InnsendtSøknadsId"]["urn"]
        .asText()
        .let { URN.rfc8141().parse(it) }
        .namespaceSpecificString()
        .toString()
}

private fun JsonMessage.getSøknadIdForRapporteringBehov(): String? {
    val søknadId = this["Søknadstidspunkt.søknad_uuid"]

    return if (søknadId.isMissingOrNull()) {
        null
    } else {
        søknadId.asText()
    }
}
