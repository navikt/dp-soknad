package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetskontekst
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.IAktivitetslogg
import no.nav.dagpenger.soknad.SpesifikkKontekst

abstract class Hendelse protected constructor(
    private val ident: String,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : IAktivitetslogg by aktivitetslogg, Aktivitetskontekst {

    init {
        aktivitetslogg.kontekst(this)
    }

    fun ident() = ident

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return this.javaClass.canonicalName.split('.').last().let { klassenavn ->
            SpesifikkKontekst(klassenavn, emptyMap())
        }
    }

    fun toLogString() = aktivitetslogg.toString()
}
