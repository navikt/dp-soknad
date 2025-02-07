package no.nav.dagpenger.soknad.livssyklus.ferdigstilt

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import de.slub.urn.URN
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.SøknadVisitor
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
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireAny("@event_name", listOf("faktum_svar", "behov"))
                    it.requireContains("@behov", behov)
                    it.forbid("@løsning")
                }
                validate {
                    it.interestedIn("InnsendtSøknadsId", "Søknadstidspunkt.søknad_uuid", "søknadId")
                    it.interestedIn("søknad_uuid", "@behovId")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        withMDC(
            mapOf(
                "søknad_uuid" to packet["søknad_uuid"].asText(),
                "behovId" to packet["@behovId"].asText(),
            ),
        ) {
            try {
                val innsendtSøknadsId = packet.søknadId ?: packet.getSøknadIdForQuizBehov() ?: packet.getSøknadIdForRapporteringBehov()
                val innsendtTidspunkt: LocalDate? =
                    mediator.hent(UUID.fromString(innsendtSøknadsId))?.let {
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

private val JsonMessage.søknadId get() = kotlin.runCatching { this["søknadId"].textValue() }.getOrNull()

// @todo: Bruker quiz dette behovet?
private fun JsonMessage.getSøknadIdForQuizBehov(): String? =
    kotlin
        .runCatching {
            this["InnsendtSøknadsId"]["urn"]
                .asText()
                .let { URN.rfc8141().parse(it) }
                .namespaceSpecificString()
                .toString()
        }.getOrNull()

// @todo: Bruker meldekort dette behovet?
private fun JsonMessage.getSøknadIdForRapporteringBehov(): String? {
    val søknadId = this["Søknadstidspunkt.søknad_uuid"]

    return if (søknadId.isMissingOrNull()) {
        null
    } else {
        søknadId.asText()
    }
}
