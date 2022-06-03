package no.nav.dagpenger.søknad.hendelse

import no.nav.dagpenger.søknad.Aktivitetslogg

class JournalførtHendelse(private val journalpostId: String, ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : Hendelse(
    ident,
    aktivitetslogg
) {
    fun journalpostId() = journalpostId
}
