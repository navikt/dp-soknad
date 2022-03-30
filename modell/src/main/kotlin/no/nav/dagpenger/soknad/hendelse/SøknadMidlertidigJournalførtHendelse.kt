package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class SøknadMidlertidigJournalførtHendelse(søknadID: UUID, journalpostId: String, ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : SøknadHendelse(
    søknadID,
    ident,
    aktivitetslogg
)
