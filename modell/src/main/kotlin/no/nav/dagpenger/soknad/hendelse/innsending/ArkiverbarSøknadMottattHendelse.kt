package no.nav.dagpenger.soknad.hendelse.innsending

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Innsending.Dokument.Dokumentvariant
import java.util.UUID

class ArkiverbarSøknadMottattHendelse(
    innsendingId: UUID,
    ident: String,
    private val dokumentvarianter: List<Dokumentvariant>,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : InnsendingHendelse(
    innsendingId,
    ident,
    aktivitetslogg
) {
    fun dokumentvarianter() = dokumentvarianter
    fun valider(): Boolean {
        return true
        // TODO: Husk å validere
    }
}
