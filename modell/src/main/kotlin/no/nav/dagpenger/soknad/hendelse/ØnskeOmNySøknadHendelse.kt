package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.soknad.Prosessnavn
import no.nav.dagpenger.soknad.Språk
import java.util.UUID

class ØnskeOmNySøknadHendelse(
    søknadID: UUID,
    ident: String,
    private val språk: String,
    val prosessnavn: Prosessnavn,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) :
    SøknadHendelse(
        søknadID,
        ident,
        aktivitetslogg,
    ) {
    fun språk(): Språk = Språk(språk)
}

class HarPåbegyntSøknadHendelse(ident: String, søknadID: UUID, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    SøknadHendelse(
        søknadID,
        ident,
        aktivitetslogg,
    )
