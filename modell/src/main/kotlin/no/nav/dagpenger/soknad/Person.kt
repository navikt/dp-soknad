package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Companion.harOpprettetSøknad
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.OpprettNySøknadHendelse
import java.util.UUID

class Person private constructor(
    private val søknader: MutableList<Søknad>,
    private val ident: String,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : Aktivitetskontekst {

    constructor(ident: String) : this(mutableListOf(), ident)

    fun håndter(opprettNySøknadHendelse: OpprettNySøknadHendelse) {
        if (harOpprettetSøknad(søknader)) {
            opprettNySøknadHendelse.severe("Kan ikke ha flere enn én opprettet søknad.")
        }

        kontekst(opprettNySøknadHendelse, "Opprettet søknad")
        søknader.add(Søknad(UUID.randomUUID()))
    }

    fun accept(visitor: PersonVisitor) {
        visitor.visitPerson(ident)
        visitor.preVisitSøknader()
        søknader.forEach { it.accept(visitor) }
        visitor.postVisitSøknader()
        aktivitetslogg.accept(visitor)
    }

    private fun kontekst(hendelse: Hendelse, melding: String) {
        hendelse.kontekst(this)
        hendelse.info(melding)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst = SpesifikkKontekst(kontekstType = "person", mapOf("ident" to ident))
}
