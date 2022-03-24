package no.nav.dagpenger.soknad

import java.util.UUID

class Søknad(private val søknadId: UUID, private val tilstand: Tilstand) : Aktivitetskontekst {

    constructor(søknadId: UUID) : this(søknadId, Opprettet)

    interface Tilstand

    object Opprettet : Tilstand

    companion object {
        internal fun harOpprettetSøknad(søknader: List<Søknad>) = søknader.any {
            it.tilstand == Opprettet
        }
    }

    fun accept(visitor: SøknadVisitor) {
        visitor.visitSøknad(søknadId, tilstand)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst = SpesifikkKontekst(kontekstType = "søknad", mapOf("søknadUUID" to søknadId.toString()))
}
