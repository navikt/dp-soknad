package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Språk
import java.util.UUID

class ØnskeOmNySøknadHendelse(søknadID: UUID, ident: String, private val språk: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    SøknadHendelse(
        søknadID, ident, aktivitetslogg
    ) {
    fun språk(): Språk = Språk(språk)
}

class ØnskeOmNyInnsendingHendelse(søknadID: UUID, ident: String, private val språk: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    SøknadHendelse(
        søknadID, ident, aktivitetslogg
    ) {
    fun språk(): Språk = Språk(språk)
}

class HarPåbegyntSøknadHendelse(ident: String, søknadID: UUID, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    SøknadHendelse(
        søknadID, ident, aktivitetslogg
    )
