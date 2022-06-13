package no.nav.dagpenger.søknad.hendelse

import no.nav.dagpenger.søknad.Aktivitetslogg
import java.util.UUID

class ØnskeOmNySøknadHendelse(søknadID: UUID, ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    SøknadHendelse(
        søknadID, ident, aktivitetslogg
    )

class HarPåbegyntSøknadHendelse(ident: String, søknadID: UUID, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    SøknadHendelse(
        søknadID, ident, aktivitetslogg
    )
