package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class JournalførtHendelse(
    søknadsId: UUID,
    private val journalpostId: String,
    ident: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : SøknadHendelse(
    søknadsId,
    ident,
    aktivitetslogg
) {
    fun journalpostId() = journalpostId
}
