package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.hendelse.OpprettNySøknadHendelse
import java.util.UUID

class Søknad(private val søknadId: UUID, private val tilstand: Tilstand) : Aktivitetskontekst {

    constructor(søknadId: UUID) : this(søknadId, Opprettet)

    interface Tilstand {
        fun håndter(opprettNySøknadHendelse: OpprettNySøknadHendelse) {
            opprettNySøknadHendelse.warn("Kan ikke håndtere opprettNySøknadHendelse")
        }
    }

    object Opprettet : Tilstand {
        override fun håndter(opprettNySøknadHendelse: OpprettNySøknadHendelse) {
            opprettNySøknadHendelse.behov(Behovtype.NySøknad, "Behøver tom søknad for denne søknaden")
        }
    }

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
