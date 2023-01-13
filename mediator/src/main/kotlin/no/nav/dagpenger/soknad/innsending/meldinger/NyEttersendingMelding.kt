package no.nav.dagpenger.soknad.innsending.meldinger

import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.utils.asUUID
import no.nav.dagpenger.soknad.utils.asZonedDateTime
import no.nav.helse.rapids_rivers.JsonMessage
import java.time.ZonedDateTime

class NyEttersendingMelding(packet: JsonMessage) {
    private val søknadId = packet["søknad_uuid"].asUUID()
    private val innsendt: ZonedDateTime = packet["innsendtTidspunkt"].asZonedDateTime()
    private val dokumentkrav: List<Innsending.Dokument> = packet.dokumentkrav()
    private val ident = packet["ident"].asText()
    private val innsending = Innsending.ettersending(innsendt, ident, søknadId, dokumentkrav)

    fun hendelse(): NyInnsendingHendelse = NyInnsendingHendelse(innsending, ident)
}
