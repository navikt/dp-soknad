package no.nav.dagpenger.søknad.hendelse

import no.nav.dagpenger.søknad.Aktivitetslogg
import java.util.UUID

class SøknadOpprettetHendelse(søknadID: UUID, ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    SøknadHendelse(
        søknadID,
        ident,
        aktivitetslogg
    )
