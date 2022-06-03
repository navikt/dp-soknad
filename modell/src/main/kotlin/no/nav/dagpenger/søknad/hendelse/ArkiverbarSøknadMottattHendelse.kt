package no.nav.dagpenger.søknad.hendelse

import no.nav.dagpenger.søknad.Aktivitetslogg
import no.nav.dagpenger.søknad.Søknad
import java.util.UUID

class ArkiverbarSøknadMottattHendelse(
    søknadID: UUID,
    ident: String,
    private val dokument: Søknad.Dokument,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : SøknadHendelse(søknadID, ident, aktivitetslogg) {

    fun dokument() = dokument
    fun valider(): Boolean {
        return true
        // TODO: Husk å validere
    }
}
