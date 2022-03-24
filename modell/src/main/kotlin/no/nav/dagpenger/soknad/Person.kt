package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Companion.harOpprettetSøknad
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import java.util.UUID

class Person private constructor(
    private val søknader: MutableList<Søknad>,
    private val ident: String,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : Aktivitetskontekst {

    constructor(ident: String) : this(mutableListOf(), ident)

    fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        if (harOpprettetSøknad(søknader)) {
            ønskeOmNySøknadHendelse.severe("Kan ikke ha flere enn én opprettet søknad.")
        }

        kontekst(ønskeOmNySøknadHendelse, "Ønske om søknad registrert")
        søknader.add(
            Søknad(UUID.randomUUID()).also {
                it.håndter(ønskeOmNySøknadHendelse)
            }
        )
    }

    fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        kontekst(søknadOpprettetHendelse, "Oppretter søknad")

        søknader.find {
            it.søknadID() == søknadOpprettetHendelse.søknadID()
        }?.håndter(søknadOpprettetHendelse) ?: søknadOpprettetHendelse.severe("Fant ikke søknaden")
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
