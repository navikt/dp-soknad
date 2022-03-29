package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class SøknadJournalførtHendelse(søknadId: UUID, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : SøknadHendelse(søknadId, aktivitetslogg)
