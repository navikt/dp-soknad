package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetskontekst
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.IAktivitetslogg
import no.nav.dagpenger.soknad.SpesifikkKontekst

abstract class Hendelse protected constructor(
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : IAktivitetslogg by aktivitetslogg, Aktivitetskontekst {

    init {
        aktivitetslogg.kontekst(this)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return this.javaClass.canonicalName.split('.').last().let { klassenavn ->
            SpesifikkKontekst(klassenavn, emptyMap())
        }
    }

    fun toLogString() = aktivitetslogg.toString()
}
