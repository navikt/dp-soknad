package no.nav.dagpenger.soknad.hendelse.innsending

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class JournalførtHendelse(
    innsendingId: UUID,
    private val journalpostId: String,
    ident: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : InnsendingHendelse(
    innsendingId,
    ident,
    aktivitetslogg
) {
    fun journalpostId() = journalpostId
}
