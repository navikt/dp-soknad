package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMotattHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentLokasjon
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import java.util.UUID

class Søknad(private val søknadId: UUID, private var tilstand: Tilstand, private var dokumentLokasjon: DokumentLokasjon?) : Aktivitetskontekst {

    constructor(søknadId: UUID) : this(søknadId, UnderOpprettelse, dokumentLokasjon = null)

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

    fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMotattHendelse) {
        if (!arkiverbarSøknadMotattHendelse.valider()) {
            arkiverbarSøknadMotattHendelse.warn("Ikke gyldig dokumentlokasjon")
            return
        }
        kontekst(arkiverbarSøknadMotattHendelse)
        tilstand.håndter(arkiverbarSøknadMotattHendelse, this)
    }

    interface Tilstand : Aktivitetskontekst {

        fun entering(søknadHendelse: SøknadHendelse, søknad: Søknad) {}

        fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) {
            ønskeOmNySøknadHendelse.warn("Kan ikke håndtere ønskeOmNySøknadHendelse")
        }

        fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse, søknad: Søknad) {
            søknadOpprettetHendelse.warn("Kan ikke håndtere søknadOpprettetHendelse")
        }

        fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) {
            søknadInnsendtHendelse.warn("Kan ikke håndtere søknadInnsendtHendelse")
        }

        fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMotattHendelse, søknad: Søknad) {
            arkiverbarSøknadMotattHendelse.warn("Kan ikke håndtere arkiverbarSøknadHendelse")
        }

        override fun toSpesifikkKontekst(): SpesifikkKontekst {
            return this.javaClass.canonicalName.split('.').last().let {
                SpesifikkKontekst(it, emptyMap())
            }
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
        override fun entering(søknadHendelse: SøknadHendelse, søknad: Søknad) {
            søknadHendelse.behov(Behovtype.ArkiverbarSøknad, "Trenger søknad på et arkiverbart format")
        }

        override fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMotattHendelse, søknad: Søknad) {
            søknad.dokumentLokasjon = arkiverbarSøknadMotattHendelse.dokumentLokasjon()
            søknad.endreTilstand(AvventerJournalføring, arkiverbarSøknadMotattHendelse)
        }
    }

    object AvventerJournalføring : Tilstand {
        override fun entering(søknadHendelse: SøknadHendelse, søknad: Søknad) {
            søknad.trengerJournalføring(søknadHendelse)
        }
    }

    private fun trengerJournalføring(søknadHendelse: SøknadHendelse) {
        val dokumentLokasjon = requireNotNull(dokumentLokasjon) {
            "OBS! Dokumentlokasjon ikke satt enda?"
        }

        søknadHendelse.behov(Behovtype.Journalføring, "Trenger å journalføre søknad", mapOf("dokumentLokasjon" to dokumentLokasjon))
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
        hendelse.kontekst(tilstand)
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
        søknadHendelse.kontekst(tilstand)
        tilstand.entering(søknadHendelse, this)
    }
}
