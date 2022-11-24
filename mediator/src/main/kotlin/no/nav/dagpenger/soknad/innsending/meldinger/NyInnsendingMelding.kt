package no.nav.dagpenger.soknad.innsending.meldinger

import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.utils.asZonedDateTime
import no.nav.helse.rapids_rivers.JsonMessage
import java.time.ZonedDateTime

class NyInnsendingMelding(packet: JsonMessage) {
    private val innsendt: ZonedDateTime = packet["innsendtTidspunkt"].asZonedDateTime()
    private val dokumentkrav: List<Innsending.Dokument> = listOf() // TODO: faktisk map dette ut
    private val ident = packet["ident"].asText()
    private val innsending = Innsending.ny(innsendt, ident, dokumentkrav)

    fun hendelse(): NyInnsendingHendelse = NyInnsendingHendelse(innsending, ident)
}
