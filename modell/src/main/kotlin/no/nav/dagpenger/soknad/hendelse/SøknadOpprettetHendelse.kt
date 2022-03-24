package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class SøknadOpprettetHendelse(søknadID: UUID, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : SøknadHendelse(søknadID, aktivitetslogg)
