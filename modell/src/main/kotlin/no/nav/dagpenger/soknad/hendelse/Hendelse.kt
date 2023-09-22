package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetskontekst
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.IAktivitetslogg
import no.nav.dagpenger.soknad.SpesifikkKontekst
import java.util.UUID

abstract class Hendelse protected constructor(
    private val ident: String,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : IAktivitetslogg by aktivitetslogg, Aktivitetskontekst {

    init {
        aktivitetslogg.kontekst(this)
    }

    fun ident() = ident

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst(this.klasseNavn, emptyMap())
    }

    protected val klasseNavn = this.javaClass.canonicalName.split('.').last()

    fun toLogString() = aktivitetslogg.toString()
}

abstract class SøknadHendelse protected constructor(
    private val søknadID: UUID,
    ident: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : Hendelse(ident = ident, aktivitetslogg = aktivitetslogg) {
    fun søknadID() = søknadID

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst(this.klasseNavn, mapOf("søknad_uuid" to søknadID().toString()))
    }
}
