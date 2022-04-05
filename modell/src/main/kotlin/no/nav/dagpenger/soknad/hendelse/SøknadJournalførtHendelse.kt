package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class SøknadJournalførtHendelse(søknadId: UUID, ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : Hendelse(
    søknadId,
    ident,
    aktivitetslogg
)
