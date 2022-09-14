package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Journalpost
import java.util.UUID

class SøknadMidlertidigJournalførtHendelse(søknadID: UUID, ident: String, private val journalpostId: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : SøknadHendelse(
    søknadID,
    ident,
    aktivitetslogg
) {
    fun journalpostId() = journalpostId
    fun journalpost(): Journalpost = TODO()
}
