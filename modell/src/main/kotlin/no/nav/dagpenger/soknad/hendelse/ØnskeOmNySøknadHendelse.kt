package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg

class ØnskeOmNySøknadHendelse(ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : Hendelse(
    ident, aktivitetslogg
)
