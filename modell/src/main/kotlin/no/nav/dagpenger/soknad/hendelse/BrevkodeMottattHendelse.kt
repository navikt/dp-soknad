package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Innsending.Brevkode
import java.util.UUID

class BrevkodeMottattHendelse(
    innsendingId: UUID,
    val søknadID: UUID,
    val ident: String,
    private val skjemaKode: String,
    private val tittel: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) :
    InnsendingHendelse(innsendingId, søknadID, ident, aktivitetslogg) {
    val brevkode: Brevkode = Brevkode(tittel, skjemaKode)
}
