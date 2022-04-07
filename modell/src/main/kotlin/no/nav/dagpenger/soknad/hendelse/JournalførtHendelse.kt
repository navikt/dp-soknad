package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg

class JournalførtHendelse(private val journalpostId: String, ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : Hendelse(
    ident,
    aktivitetslogg
) {
    fun journalpostId() = journalpostId
}
