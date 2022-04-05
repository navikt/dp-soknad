package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentLokasjon
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
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

    companion object {
        internal fun List<Søknad>.harAlleredeOpprettetSøknad() = this.any { it.tilstand == UnderOpprettelse }
        internal fun List<Søknad>.finnSøknad(søknadId: UUID): Søknad? = this.find { it.søknadId == søknadId }
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

    fun håndter(søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse) {
        kontekst(søknadMidlertidigJournalførtHendelse)
        tilstand.håndter(søknadMidlertidigJournalførtHendelse, this)
    }

    fun håndter(søknadJournalførtHendelse: SøknadJournalførtHendelse) {
        kontekst(søknadJournalførtHendelse)
        tilstand.håndter(søknadJournalførtHendelse, this)
    }

    interface Tilstand : Aktivitetskontekst {

        val tilstandType: Type

        fun entering(søknadHendelse: SøknadHendelse, søknad: Søknad) {}

        fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) =
            ønskeOmNySøknadHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse, søknad: Søknad) =
            søknadOpprettetHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) =
            søknadInnsendtHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse, søknad: Søknad) =
            arkiverbarSøknadMotattHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse, søknad: Søknad) =
            søknadMidlertidigJournalførtHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søknadJournalførtHendelse: SøknadJournalførtHendelse, søknad: Søknad) =
            søknadJournalførtHendelse.`kan ikke håndteres i denne tilstanden`()

        private fun Hendelse.`kan ikke håndteres i denne tilstanden`() =
            this.warn("Kan ikke håndtere ${this.javaClass.simpleName} i tilstand $tilstandType")

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
            Påbegynt,
            AvventerArkiverbarSøknad,
            AvventerMidlertidligJournalføring,
            AvventerJournalføring,
            Journalført
        }
    }

    private object UnderOpprettelse : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.UnderOpprettelse

        override fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) {
            ønskeOmNySøknadHendelse.behov(Behovtype.NySøknad, "Behov for å starte søknadsprosess")
        }

        override fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse, søknad: Søknad) {
            søknad.endreTilstand(Påbegynt, søknadOpprettetHendelse)
        }
    }

    private object Påbegynt : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.Påbegynt

        override fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) {
            søknad.endreTilstand(AvventerArkiverbarSøknad, søknadInnsendtHendelse)
        }
    }

    private object AvventerArkiverbarSøknad : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.AvventerArkiverbarSøknad

        override fun entering(søknadHendelse: SøknadHendelse, søknad: Søknad) {
            søknadHendelse.behov(Behovtype.ArkiverbarSøknad, "Trenger søknad på et arkiverbart format")
            // TODO: Emit en hendelse som fører til at vi besvarer faktum i quiz for når søknaden/kravet ble fremsatt
        }

        override fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse, søknad: Søknad) {
            søknad.dokumentLokasjon = arkiverbarSøknadMotattHendelse.dokumentLokasjon()
            søknad.endreTilstand(AvventerMidlertidligJournalføring, arkiverbarSøknadMotattHendelse)
        }
    }

    private object AvventerMidlertidligJournalføring : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.AvventerMidlertidligJournalføring

        override fun entering(søknadHendelse: SøknadHendelse, søknad: Søknad) {
            søknad.trengerNyJournalpost(søknadHendelse)
        }

        override fun håndter(
            søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse,
            søknad: Søknad
        ) {
            søknad.endreTilstand(AvventerJournalføring, søknadMidlertidigJournalførtHendelse)
        }
    }

    private object AvventerJournalføring : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.AvventerJournalføring

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
        SpesifikkKontekst(kontekstType = "søknad", mapOf("søknad_uuid" to søknadId.toString()))

    private fun kontekst(hendelse: Hendelse) {
        hendelse.kontekst(this)
        hendelse.kontekst(tilstand)
    }

    private fun trengerNyJournalpost(søknadHendelse: SøknadHendelse) {
        val dokumentLokasjon = requireNotNull(dokumentLokasjon) {
            "Forventet at variabel dokumentLokasjon var satt. Er i tilstand: $tilstand"
        }

        søknadHendelse.behov(
            Behovtype.NyJournalpost,
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
