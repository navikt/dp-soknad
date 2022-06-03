package no.nav.dagpenger.søknad.hendelse

import no.nav.dagpenger.søknad.Aktivitetslogg
import java.util.UUID

class SøknadMidlertidigJournalførtHendelse(søknadID: UUID, ident: String, private val journalpostId: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : SøknadHendelse(
    søknadID,
    ident,
    aktivitetslogg
) {
    fun journalpostId() = journalpostId
}
