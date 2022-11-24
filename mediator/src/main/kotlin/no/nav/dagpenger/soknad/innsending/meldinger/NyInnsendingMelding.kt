package no.nav.dagpenger.soknad.innsending.meldinger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.dagpenger.soknad.Innsending
import no.nav.helse.rapids_rivers.JsonMessage
import java.time.ZonedDateTime

class NyInnsendingMelding(packet: JsonMessage) {
    private val innsendt: ZonedDateTime = packet["innsendtTidspunkt"].asZonedDateTime()
    private val dokumentkrav: List<Innsending.Dokument> = listOf() // TODO: faktisk map dette ut
    private val innsending = Innsending.ny(innsendt, dokumentkrav)
    private val ident = packet["ident"].asText()

    fun hendelse(): NyInnsendingHendelse = NyInnsendingHendelse(innsending, ident)
}

private fun JsonNode.asZonedDateTime(): ZonedDateTime =
    asText().let { ZonedDateTime.parse(it) }
