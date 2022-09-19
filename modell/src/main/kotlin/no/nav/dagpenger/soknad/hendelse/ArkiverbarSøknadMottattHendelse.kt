package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Innsending
import java.util.UUID

class ArkiverbarSøknadMottattHendelse(
    innsendingId: UUID,
    søknadID: UUID,
    ident: String,
    private val dokumentvarianter: List<Innsending.Dokument.Dokumentvariant>,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : InnsendingHendelse(
    innsendingId,
    søknadID,
    ident,
    aktivitetslogg
) {
    fun dokumentvarianter() = dokumentvarianter
    fun valider(): Boolean {
        return true
        // TODO: Husk å validere
    }
}
