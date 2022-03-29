package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg

class ØnskeOmNySøknadHendelse(private val ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : Hendelse(aktivitetslogg) {
    fun ident() = ident
}
