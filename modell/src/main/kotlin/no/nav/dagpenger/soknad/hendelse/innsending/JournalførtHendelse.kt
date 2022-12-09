package no.nav.dagpenger.soknad.hendelse.innsending

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class JournalførtHendelse(
    innsendingId: UUID,
    ident: String,
    private val journalpostId: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : InnsendingHendelse(
    innsendingId,
    ident,
    aktivitetslogg
) {
    fun journalpostId() = journalpostId
}
