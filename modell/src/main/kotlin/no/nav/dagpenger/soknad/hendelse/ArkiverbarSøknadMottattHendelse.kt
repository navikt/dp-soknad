package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Innsending
import java.util.UUID

class ArkiverbarSøknadMottattHendelse(
    søknadID: UUID,
    ident: String,
    private val dokument: Innsending.Dokument,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : SøknadHendelse(søknadID, ident, aktivitetslogg) {

    fun dokument() = dokument
    fun valider(): Boolean {
        return true
        // TODO: Husk å validere
    }
}
