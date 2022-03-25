package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import java.util.UUID

class Søknad(private val søknadId: UUID, private var tilstand: Tilstand) : Aktivitetskontekst {

    constructor(søknadId: UUID) : this(søknadId, UnderOpprettelse)

    internal fun søknadID() = søknadId

    fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        kontekst(ønskeOmNySøknadHendelse)
        tilstand.håndter(ønskeOmNySøknadHendelse, this)
    }

    fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        kontekst(søknadOpprettetHendelse)
        tilstand.håndter(søknadOpprettetHendelse, this)
    }

    fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse) {
        kontekst(søknadInnsendtHendelse)
        tilstand.håndter(søknadInnsendtHendelse, this)
    }

    interface Tilstand {

        fun vedAktivering(søknadHendelse: SøknadHendelse) {}

        fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) {
            ønskeOmNySøknadHendelse.warn("Kan ikke håndtere ønskeOmNySøknadHendelse")
        }

        fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse, søknad: Søknad) {
            søknadOpprettetHendelse.warn("Kan ikke håndtere søknadOpprettetHendelse")
        }

        fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) {
            søknadInnsendtHendelse.warn("Kan ikke håndtere søknadInnsendtHendelse")
        }
    }

    object UnderOpprettelse : Tilstand {
        override fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) {
            ønskeOmNySøknadHendelse.behov(Behovtype.NySøknad, "Behøver tom søknad for denne søknaden")
        }
        override fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse, søknad: Søknad) {
            søknad.endreTilstand(UnderArbeid, søknadOpprettetHendelse)
        }
    }

    object UnderArbeid : Tilstand {
        override fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) {
            søknad.endreTilstand(AvventerArkiverbarSøknad, søknadInnsendtHendelse)
        }
    }

    object AvventerArkiverbarSøknad : Tilstand {
        override fun vedAktivering(søknadInnsendtHendelse: SøknadHendelse) {
            søknadInnsendtHendelse.behov(Behovtype.ArkiverbarSøknad, "Trenger søknad på et arkiverbart format")
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

    private fun endreTilstand(
        nyTilstand: Tilstand,
        søknadHendelse: SøknadHendelse
    ) {
        if (nyTilstand == tilstand) {
            return // Vi er allerede i tilstanden
        }
        val forrigeTilstand = tilstand
        tilstand = nyTilstand
        tilstand.vedAktivering(søknadHendelse)
    }
}
