package no.nav.dagpenger.soknad.hendelse.innsending

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.soknad.Innsending.Metadata
import java.util.UUID

class InnsendingMetadataMottattHendelse(
    innsendingId: UUID,
    ident: String,
    skjemaKode: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : InnsendingHendelse(innsendingId, ident, aktivitetslogg) {
    val metadata: Metadata = Metadata(skjemaKode)
}
