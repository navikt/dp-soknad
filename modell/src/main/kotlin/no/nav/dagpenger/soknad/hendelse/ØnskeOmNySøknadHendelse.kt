package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class ØnskeOmNySøknadHendelse(ident: String, søknadID: UUID, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : Hendelse(
    søknadID, ident, aktivitetslogg
)
