package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import java.util.UUID

class Søknad(private val søknadId: UUID, private val tilstand: Tilstand) : Aktivitetskontekst {

    constructor(søknadId: UUID) : this(søknadId, UnderOpprettelse)

    internal fun søknadID() = søknadId

    fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        kontekst(ønskeOmNySøknadHendelse)
        tilstand.håndter(ønskeOmNySøknadHendelse)
    }

    fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        kontekst(søknadOpprettetHendelse)
        tilstand.håndter(søknadOpprettetHendelse)
    }

    interface Tilstand {
        fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
            ønskeOmNySøknadHendelse.warn("Kan ikke håndtere ønskeOmNySøknadHendelse")
        }

        fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
            søknadOpprettetHendelse.warn("Kan ikke håndtere søknadOpprettetHendelse")
        }
    }

    object UnderOpprettelse : Tilstand {
        override fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
            ønskeOmNySøknadHendelse.behov(Behovtype.NySøknad, "Behøver tom søknad for denne søknaden")
        }
    }

    companion object {
        internal fun harOpprettetSøknad(søknader: List<Søknad>) = søknader.any {
            it.tilstand == UnderOpprettelse
        }
    }

    fun accept(visitor: SøknadVisitor) {
        visitor.visitSøknad(søknadId, tilstand)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst = SpesifikkKontekst(kontekstType = "søknad", mapOf("søknadUUID" to søknadId.toString()))

    private fun kontekst(hendelse: Hendelse) {
        hendelse.kontekst(this)
    }
}
