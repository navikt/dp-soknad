package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Innsending.Dokument.Dokumentvariant
import no.nav.dagpenger.soknad.NyInnsending
import java.util.UUID

class ArkiverbarSøknadMottattHendelse(
        innsendingId: UUID,
        søknadID: UUID,
        ident: String,
        private val dokumentvarianter: List<Dokumentvariant>,
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
