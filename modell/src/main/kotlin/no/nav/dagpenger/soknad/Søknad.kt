package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentLokasjon
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import java.util.UUID

class Søknad private constructor(
    private val søknadId: UUID,
    private val person: Person,
    private var tilstand: Tilstand,
    private var dokumentLokasjon: DokumentLokasjon?,
) : Aktivitetskontekst {

    internal constructor(søknadId: UUID, person: Person) : this(
        søknadId,
        person,
        UnderOpprettelse,
        dokumentLokasjon = null
    )

    internal fun søknadID() = søknadId

    companion object {
        internal fun harOpprettetSøknad(søknader: List<Søknad>) = søknader.any {
            it.tilstand == UnderOpprettelse
        }
    }

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

    fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse) {
        if (!arkiverbarSøknadMotattHendelse.valider()) {
            arkiverbarSøknadMotattHendelse.warn("Ikke gyldig dokumentlokasjon")
            return
        }
        kontekst(arkiverbarSøknadMotattHendelse)
        tilstand.håndter(arkiverbarSøknadMotattHendelse, this)
    }

    fun håndter(søknadJournalførtHendelse: SøknadJournalførtHendelse) {
        kontekst(søknadJournalførtHendelse)
        tilstand.håndter(søknadJournalførtHendelse, this)
    }

    interface Tilstand : Aktivitetskontekst {

        val tilstandType: Type

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

        fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse, søknad: Søknad) {
            arkiverbarSøknadMotattHendelse.warn("Kan ikke håndtere arkiverbarSøknadHendelse")
        }

        fun håndter(søknadJournalførtHendelse: SøknadJournalførtHendelse, søknad: Søknad) {
            søknadJournalførtHendelse.warn("Kan ikke håndtere søknadJournalførtHendelse")
        }

        override fun toSpesifikkKontekst(): SpesifikkKontekst {
            return this.javaClass.canonicalName.split('.').last().let {
                SpesifikkKontekst(it, emptyMap())
            }
        }

        fun accept(visitor: TilstandVisitor) {
            visitor.visitTilstand(tilstandType)
        }

        enum class Type {
            UnderOpprettelse,
            UnderArbeid,
            AvventerArkiverbarSøknad,
            AvventerJournalføring,
            Journalført
        }
    }

    private object UnderOpprettelse : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.UnderOpprettelse

        override fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) {
            ønskeOmNySøknadHendelse.behov(Behovtype.NySøknad, "Behøver tom søknad for denne søknaden")
        }

        override fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse, søknad: Søknad) {
            søknad.endreTilstand(UnderArbeid, søknadOpprettetHendelse)
        }
    }

    private object UnderArbeid : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.UnderArbeid

        override fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) {
            søknad.endreTilstand(AvventerArkiverbarSøknad, søknadInnsendtHendelse)
        }
    }

    private object AvventerArkiverbarSøknad : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.AvventerArkiverbarSøknad

        override fun entering(søknadHendelse: SøknadHendelse, søknad: Søknad) {
            søknadHendelse.behov(Behovtype.ArkiverbarSøknad, "Trenger søknad på et arkiverbart format")
        }

        override fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse, søknad: Søknad) {
            søknad.dokumentLokasjon = arkiverbarSøknadMotattHendelse.dokumentLokasjon()
            søknad.endreTilstand(AvventerJournalføring, arkiverbarSøknadMotattHendelse)
        }
    }

    private object AvventerJournalføring : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.AvventerJournalføring

        override fun entering(søknadHendelse: SøknadHendelse, søknad: Søknad) {
            søknad.trengerJournalføring(søknadHendelse)
        }

        override fun håndter(søknadJournalførtHendelse: SøknadJournalførtHendelse, søknad: Søknad) {
            søknad.endreTilstand(Journalført, søknadJournalførtHendelse)
        }
    }

    private object Journalført : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.Journalført
    }

    fun accept(visitor: SøknadVisitor) {
        visitor.visitSøknad(søknadId)
        tilstand.accept(visitor)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst =
        SpesifikkKontekst(kontekstType = "søknad", mapOf("søknadUUID" to søknadId.toString()))

    private fun kontekst(hendelse: Hendelse) {
        hendelse.kontekst(this)
        hendelse.kontekst(tilstand)
    }

    private fun trengerJournalføring(søknadHendelse: SøknadHendelse) {
        val dokumentLokasjon = requireNotNull(dokumentLokasjon) {
            "Forventet at variabel dokumentLokasjon var satt. Er i tilstand: $tilstand"
        }

        søknadHendelse.behov(
            Behovtype.Journalføring,
            "Trenger å journalføre søknad",
            mapOf("dokumentLokasjon" to dokumentLokasjon)
        )
    }

    private fun endreTilstand(nyTilstand: Tilstand, søknadHendelse: SøknadHendelse) {
        if (nyTilstand == tilstand) {
            return // Vi er allerede i tilstanden
        }
        val forrigeTilstand = tilstand
        tilstand = nyTilstand
        søknadHendelse.kontekst(tilstand)
        tilstand.entering(søknadHendelse, this)

        varsleOmEndretTilstand(forrigeTilstand)
    }

    private fun varsleOmEndretTilstand(forrigeTilstand: Tilstand) {
        person.søknadTilstandEndret(
            PersonObserver.SøknadEndretTilstandEvent(
                søknadId = søknadId,
                gjeldendeTilstand = tilstand.tilstandType,
                forrigeTilstand = forrigeTilstand.tilstandType
            )
        )
    }
}
