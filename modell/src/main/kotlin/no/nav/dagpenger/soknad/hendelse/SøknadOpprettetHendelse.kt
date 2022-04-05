package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class SøknadOpprettetHendelse(søknadID: UUID, ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : Hendelse(
    søknadID,
    ident,
    aktivitetslogg
)
