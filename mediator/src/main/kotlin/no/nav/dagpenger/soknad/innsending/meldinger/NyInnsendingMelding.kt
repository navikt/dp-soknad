package no.nav.dagpenger.soknad.innsending.meldinger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyEttersending
import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype.NyInnsending
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Innsending.InnsendingType.ETTERSENDING_TIL_DIALOG
import no.nav.dagpenger.soknad.Innsending.InnsendingType.NY_DIALOG
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.dagpenger.soknad.utils.asZonedDateTime
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.isMissingOrNull
import java.time.ZonedDateTime

class NyInnsendingMelding(packet: JsonMessage) {
    private val søknadId = packet["søknad_uuid"].asUUID()
    private val innsendt: ZonedDateTime = packet["innsendtTidspunkt"].asZonedDateTime()
    private val dokumentkrav: List<Innsending.Dokument> = packet.dokumentkrav()
    private val ident = packet["ident"].asText()
    private val innsending = when (packet.innsendingType()) {
        NY_DIALOG -> Innsending.ny(innsendt, ident, søknadId, dokumentkrav)
        ETTERSENDING_TIL_DIALOG -> Innsending.ettersending(innsendt, ident, søknadId, dokumentkrav)
    }

    fun hendelse(): NyInnsendingHendelse = NyInnsendingHendelse(innsending, ident)

    companion object {
        fun JsonMessage.behov() = this["@behov"].single().asText()
        fun JsonMessage.innsendingType(): Innsending.InnsendingType {
            return when (val behovtype = this.behov()) {
                NyInnsending.name -> NY_DIALOG
                NyEttersending.name -> ETTERSENDING_TIL_DIALOG
                else -> {
                    throw IllegalArgumentException("Ukjent behovtype: $behovtype")
                }
            }
        }

        fun JsonMessage.dokumentkrav(): List<Innsending.Dokument> {
            return this["dokumentkrav"].map { jsonNode ->
                Innsending.Dokument(
                    uuid = jsonNode["uuid"].asUUID(),
                    kravId = jsonNode["kravId"].asNullableText(),
                    skjemakode = jsonNode["skjemakode"].asNullableText(),
                    varianter = jsonNode.varianter()
                )
            }
        }

        fun JsonNode.varianter(): List<Innsending.Dokument.Dokumentvariant> {
            return this["varianter"].map { jsonNode ->
                Innsending.Dokument.Dokumentvariant(
                    uuid = jsonNode["uuid"].asUUID(),
                    filnavn = jsonNode["filnavn"].asText(),
                    urn = jsonNode["urn"].asText(),
                    variant = jsonNode["variant"].asText(),
                    type = jsonNode["type"].asText()
                )
            }
        }

        fun JsonNode.asNullableText(): String? {
            return if (this.isMissingOrNull()) {
                null
            } else {
                this.asText()
            }
        }
    }
}
