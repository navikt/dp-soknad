package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class SøknadMidlertidigJournalførtHendelse(
    innsendingId: UUID,
    søknadID: UUID,
    ident: String,
    private val journalpostId: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : InnsendingHendelse(
    innsendingId,
    søknadID,
    ident,
    aktivitetslogg
) {
    fun journalpostId() = journalpostId
}
