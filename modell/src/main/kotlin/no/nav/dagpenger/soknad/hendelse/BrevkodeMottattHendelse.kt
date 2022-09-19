package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Innsending.Brevkode
import java.util.UUID

class BrevkodeMottattHendelse(innsendingId: UUID, val søknadID: UUID, val ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    InnsendingHendelse(innsendingId, søknadID, ident, aktivitetslogg) {
    fun brevkode(): Brevkode {
        return Brevkode("Søknad om dagpenger", "04-01.02")
    }
}
