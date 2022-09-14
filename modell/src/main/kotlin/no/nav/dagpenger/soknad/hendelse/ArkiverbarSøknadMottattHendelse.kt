package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Søknad
import java.util.UUID

class ArkiverbarSøknadMottattHendelse(
    søknadID: UUID,
    ident: String,
    private val journalpost: Søknad.Journalpost,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
    val arkiverbartDokument: List<Søknad.Journalpost.Variant>
) : SøknadHendelse(søknadID, ident, aktivitetslogg) {

    fun dokument() = journalpost
    fun valider(): Boolean {
        return true
        // TODO: Husk å validere
    }
}
