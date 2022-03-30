package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class ArkiverbarSøknadMottattHendelse(
    søknadID: UUID,
    ident: String,
    private val dokumentLokasjon: DokumentLokasjon,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : SøknadHendelse(søknadID, ident, aktivitetslogg) {

    internal fun dokumentLokasjon() = dokumentLokasjon
    fun valider(): Boolean {
        return true
        // TODO: Husk å validere
    }
}

typealias DokumentLokasjon = String
