package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Innsending.Metadata
import java.util.UUID

class InnsendingMetadataMottattHendelse(
    innsendingId: UUID,
    val søknadID: UUID,
    val ident: String,
    skjemaKode: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) :
    InnsendingHendelse(innsendingId, søknadID, ident, aktivitetslogg) {
    val metadata: Metadata = Metadata(skjemaKode)
}
