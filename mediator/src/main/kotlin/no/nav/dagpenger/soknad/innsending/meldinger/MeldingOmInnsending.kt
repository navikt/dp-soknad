package no.nav.dagpenger.soknad.innsending.meldinger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.dagpenger.soknad.utils.asZonedDateTime
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.isMissingOrNull
import java.time.ZonedDateTime

abstract class MeldingOmInnsending(packet: JsonMessage) {
    protected val søknadId = packet["søknad_uuid"].asUUID()
    protected val innsendt: ZonedDateTime = packet["innsendtTidspunkt"].asZonedDateTime()
    protected val dokumentkrav: List<Innsending.Dokument> = packet.dokumentkrav()
    protected val ident = packet["ident"].asText()

    abstract val innsending: Innsending
    abstract fun hendelse(): Hendelse

    private fun JsonMessage.dokumentkrav(): List<Innsending.Dokument> {
        return this["dokumentkrav"].map { jsonNode ->
            Innsending.Dokument(
                uuid = jsonNode["uuid"].asUUID(),
                kravId = jsonNode["kravId"].asNullableText(),
                skjemakode = jsonNode["skjemakode"].asNullableText(),
                varianter = jsonNode.varianter()
            )
        }
    }

    private fun JsonNode.varianter(): List<Innsending.Dokument.Dokumentvariant> {
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

    private fun JsonNode.asNullableText(): String? {
        return if (this.isMissingOrNull()) {
            null
        } else {
            this.asText()
        }
    }
}
