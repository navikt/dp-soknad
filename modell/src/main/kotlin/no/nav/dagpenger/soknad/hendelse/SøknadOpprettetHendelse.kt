package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Prosessversjon
import java.util.UUID

class SøknadOpprettetHendelse(
    private val prosessversjon: Prosessversjon,
    søknadID: UUID,
    ident: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) :
    SøknadHendelse(
        søknadID,
        ident,
        aktivitetslogg
    ) {
    fun prosessversjon() = prosessversjon
}
