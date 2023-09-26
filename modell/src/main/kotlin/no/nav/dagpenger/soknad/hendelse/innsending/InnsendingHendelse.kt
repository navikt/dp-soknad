package no.nav.dagpenger.soknad.hendelse.innsending

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.soknad.hendelse.Hendelse
import java.util.UUID

abstract class InnsendingHendelse(
    val innsendingId: UUID,
    ident: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : Hendelse(ident = ident, aktivitetslogg = aktivitetslogg) {
    fun innsendingId() = innsendingId

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst(this.klasseNavn, mapOf("innsendingId" to innsendingId().toString()))
    }
}
