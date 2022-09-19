package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class JournalførtHendelse(
    innsendingId: UUID,
    søknadsId: UUID,
    private val journalpostId: String,
    ident: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : InnsendingHendelse(
    innsendingId,
    søknadsId,
    ident,
    aktivitetslogg
) {
    fun journalpostId() = journalpostId
}
