package no.nav.dagpenger.soknad.hendelse.innsending

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.hendelse.Hendelse

class JournalførtHendelse(
    ident: String,
    private val journalpostId: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : Hendelse(
    ident,
    aktivitetslogg
) {
    fun journalpostId() = journalpostId
}
