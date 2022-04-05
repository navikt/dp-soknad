package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class SøknadMidlertidigJournalførtHendelse(søknadID: UUID, ident: String, journalpostId: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : Hendelse(
    søknadID,
    ident,
    aktivitetslogg
)
