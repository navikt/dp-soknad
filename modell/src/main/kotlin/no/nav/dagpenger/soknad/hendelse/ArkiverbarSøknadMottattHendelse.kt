package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class ArkiverbarSøknadMottattHendelse(
    søknadID: UUID,
    private val dokumentLokasjon: DokumentLokasjon,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : SøknadHendelse(søknadID, aktivitetslogg) {

    internal fun dokumentLokasjon() = dokumentLokasjon
    fun valider(): Boolean {
        return true
        // TODO: Husk å validere
    }
}

typealias DokumentLokasjon = String
