package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.SøknadObserver.SøknadSlettetEvent
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.HarPåbegyntSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNyInnsendingHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import java.time.ZonedDateTime
import java.util.UUID

class Søknad private constructor(
    private val søknadId: UUID,
    private val søknadObserver: SøknadObserver,
    private val ident: String,
    // @todo: Endre navn fra dokument til journalføringer? (og liste)?
    private var tilstand: Tilstand,
    private var dokument: Dokument?,
    private var journalpostId: String?,
    private var innsendtTidspunkt: ZonedDateTime?,
    private val språk: Språk,
    private val dokumentkrav: Dokumentkrav,
    private var sistEndretAvBruker: ZonedDateTime?,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : Aktivitetskontekst {

    constructor(søknadId: UUID, språk: Språk, søknadObserver: SøknadObserver, ident: String) : this(
        søknadId = søknadId,
        søknadObserver = søknadObserver,
        ident = ident,
        tilstand = UnderOpprettelse,
        dokument = null,
        journalpostId = null,
        innsendtTidspunkt = null,
        språk = språk,
        dokumentkrav = Dokumentkrav(),
        sistEndretAvBruker = null
    )

    companion object {
        internal fun List<Søknad>.harAlleredeOpprettetSøknad() =
            this.any { it.tilstand == UnderOpprettelse || it.tilstand == Påbegynt }

        internal fun List<Søknad>.harPåbegyntSøknad(): Boolean = this.filter { it.tilstand == Påbegynt }.size == 1
        internal fun List<Søknad>.hentPåbegyntSøknad(): Søknad = this.single { it.tilstand == Påbegynt }
        internal fun List<Søknad>.finnSøknad(søknadId: UUID): Søknad? = this.find { it.søknadId == søknadId }
        internal fun List<Søknad>.finnSøknad(journalpostId: String): Søknad? =
            this.find { it.journalpostId == journalpostId }

        fun rehydrer(
            søknadId: UUID,
            søknadObserver: SøknadObserver,
            ident: String,
            dokument: Dokument?,
            journalpostId: String?,
            innsendtTidspunkt: ZonedDateTime?,
            språk: Språk,
            dokumentkrav: Dokumentkrav,
            sistEndretAvBruker: ZonedDateTime?,
            tilstandsType: String
        ): Søknad {
            val tilstand: Tilstand = when (Tilstand.Type.valueOf(tilstandsType)) {
                Tilstand.Type.UnderOpprettelse -> UnderOpprettelse
                Tilstand.Type.Påbegynt -> Påbegynt
                Tilstand.Type.AvventerArkiverbarSøknad -> AvventerArkiverbarSøknad
                Tilstand.Type.AvventerMidlertidligJournalføring -> AvventerMidlertidligJournalføring
                Tilstand.Type.AvventerJournalføring -> AvventerJournalføring
                Tilstand.Type.Journalført -> Journalført
                Tilstand.Type.Slettet -> throw IllegalArgumentException("Kan ikke rehydrere slettet søknad med id $søknadId")
            }
            return Søknad(
                søknadId = søknadId,
                søknadObserver = søknadObserver,
                ident = ident,
                tilstand = tilstand,
                dokument = dokument,
                journalpostId = journalpostId,
                innsendtTidspunkt = innsendtTidspunkt,
                språk = språk,
                dokumentkrav = dokumentkrav,
                sistEndretAvBruker = sistEndretAvBruker
            )
        }
    }

    fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        kontekst(ønskeOmNySøknadHendelse)
        tilstand.håndter(ønskeOmNySøknadHendelse, this)
    }

    fun håndter(ønskeOmNyInnsendingHendelse: ØnskeOmNyInnsendingHendelse) {
        kontekst(ønskeOmNyInnsendingHendelse)
        tilstand.håndter(ønskeOmNyInnsendingHendelse, this)
    }

    fun håndter(harPåbegyntSøknadHendelse: HarPåbegyntSøknadHendelse) {
        kontekst(harPåbegyntSøknadHendelse)
        tilstand.håndter(harPåbegyntSøknadHendelse, this)
    }

    fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        kontekst(søknadOpprettetHendelse)
        tilstand.håndter(søknadOpprettetHendelse, this)
    }

    fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse) {
        kontekst(søknadInnsendtHendelse)
        sistEndretAvBruker = ZonedDateTime.now()
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

    fun håndter(journalførtHendelse: JournalførtHendelse) {
        kontekst(journalførtHendelse)
        tilstand.håndter(journalførtHendelse, this)
    }

    fun håndter(faktumOppdatertHendelse: FaktumOppdatertHendelse) {
        kontekst(faktumOppdatertHendelse)
        if (tilstand == Påbegynt) {
            sistEndretAvBruker = ZonedDateTime.now()
            tilstand.håndter(faktumOppdatertHendelse, this)
        } else {
            faktumOppdatertHendelse.severe("Kan ikke oppdatere faktum for søknader i tilstand ${tilstand.tilstandType.name}")
        }
    }

    fun håndter(søkeroppgaveHendelse: SøkeroppgaveHendelse) {
        kontekst(søkeroppgaveHendelse)
        tilstand.håndter(søkeroppgaveHendelse, this)
    }

    fun håndter(slettSøknadHendelse: SlettSøknadHendelse) {
        kontekst(slettSøknadHendelse)
        tilstand.håndter(slettSøknadHendelse, this)
    }

    fun håndter(dokumentasjonIkkeTilgjengelig: DokumentasjonIkkeTilgjengelig) {
        kontekst(dokumentasjonIkkeTilgjengelig)
        tilstand.håndter(dokumentasjonIkkeTilgjengelig, this)
    }

    fun håndter(leggTilFil: LeggTilFil) {
        kontekst(leggTilFil)
        tilstand.håndter(leggTilFil, this)
    }

    fun håndter(slettFil: SlettFil) {
        kontekst(slettFil)
        tilstand.håndter(slettFil, this)
    }

    interface Tilstand : Aktivitetskontekst {

        val tilstandType: Type

        fun entering(søknadHendelse: Hendelse, søknad: Søknad) {}

        fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) =
            ønskeOmNySøknadHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(ønskeOmNyInnsendingHendelse: ØnskeOmNyInnsendingHendelse, søknad: Søknad) =
            ønskeOmNyInnsendingHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(harPåbegyntSøknadHendelse: HarPåbegyntSøknadHendelse, søknad: Søknad) =
            harPåbegyntSøknadHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse, søknad: Søknad) =
            søknadOpprettetHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) =
            søknadInnsendtHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse, søknad: Søknad) =
            arkiverbarSøknadMotattHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse, søknad: Søknad) =
            søknadMidlertidigJournalførtHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(journalførtHendelse: JournalførtHendelse, søknad: Søknad) =
            journalførtHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(faktumOppdatertHendelse: FaktumOppdatertHendelse, søknad: Søknad) =
            faktumOppdatertHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søkeroppgaveHendelse: SøkeroppgaveHendelse, søknad: Søknad) {
            søkeroppgaveHendelse.`kan ikke håndteres i denne tilstanden`()
        }

        fun håndter(slettSøknadHendelse: SlettSøknadHendelse, søknad: Søknad) {
            slettSøknadHendelse.severe("Kan ikke slette søknad i tilstand $tilstandType")
        }

        fun håndter(dokumentasjonIkkeTilgjengelig: DokumentasjonIkkeTilgjengelig, søknad: Søknad) {
            dokumentasjonIkkeTilgjengelig.`kan ikke håndteres i denne tilstanden`()
        }
        fun håndter(leggTilFil: LeggTilFil, søknad: Søknad) {
            leggTilFil.`kan ikke håndteres i denne tilstanden`()
        }

        fun håndter(slettFil: SlettFil, søknad: Søknad) {
            slettFil.`kan ikke håndteres i denne tilstanden`()
        }

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
            Journalført,
            Slettet
        }
    }

    private object UnderOpprettelse : Tilstand {

        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.UnderOpprettelse

        override fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) {
            ønskeOmNySøknadHendelse.behov(Behovtype.NySøknad, "Behov for å starte søknadsprosess")
        }

        override fun håndter(ønskeOmNyInnsendingHendelse: ØnskeOmNyInnsendingHendelse, søknad: Søknad) {
            ønskeOmNyInnsendingHendelse.behov(Behovtype.NyInnsending, "Behov for å starte ny innsending")
        }

        override fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse, søknad: Søknad) {
            søknad.endreTilstand(Påbegynt, søknadOpprettetHendelse)
        }
    }

    private object Påbegynt : Tilstand {

        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.Påbegynt

        override fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) {
            søknad.innsendtTidspunkt = søknadInnsendtHendelse.innsendtidspunkt()
            søknad.endreTilstand(AvventerArkiverbarSøknad, søknadInnsendtHendelse)
        }

        override fun håndter(faktumOppdatertHendelse: FaktumOppdatertHendelse, søknad: Søknad) {
        }

        override fun håndter(harPåbegyntSøknadHendelse: HarPåbegyntSøknadHendelse, søknad: Søknad) {
        }

        override fun håndter(dokumentasjonIkkeTilgjengelig: DokumentasjonIkkeTilgjengelig, søknad: Søknad) {
            søknad.dokumentkrav.håndter(dokumentasjonIkkeTilgjengelig)
        }

        override fun håndter(leggTilFil: LeggTilFil, søknad: Søknad) {
            søknad.dokumentkrav.håndter(leggTilFil)
        }

        override fun håndter(søkeroppgaveHendelse: SøkeroppgaveHendelse, søknad: Søknad) {
            søkeroppgaveHendelse.info("Fikk %d sannsynliggjøringer", søkeroppgaveHendelse.sannsynliggjøringer().size)
            søknad.håndter(søkeroppgaveHendelse.sannsynliggjøringer())
        }

        override fun håndter(slettFil: SlettFil, søknad: Søknad) {
            søknad.dokumentkrav.håndter(slettFil)
        }

        override fun håndter(slettSøknadHendelse: SlettSøknadHendelse, søknad: Søknad) {
            søknad.endreTilstand(Slettet, slettSøknadHendelse)
        }
    }

    private fun håndter(nyeSannsynliggjøringer: Set<Sannsynliggjøring>) {
        this.dokumentkrav.håndter(nyeSannsynliggjøringer)
    }

    private object Slettet : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.Slettet

        override fun entering(søknadHendelse: Hendelse, søknad: Søknad) {
            søknad.slettet(søknad.ident)
        }
    }

    private fun slettet(ident: String) {
        søknadObserver.søknadSlettet(SøknadSlettetEvent(søknadId, ident))
    }

    private object AvventerArkiverbarSøknad : Tilstand {

        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.AvventerArkiverbarSøknad

        override fun entering(søknadHendelse: Hendelse, søknad: Søknad) {
            val innsendtTidspunkt =
                requireNotNull(søknad.innsendtTidspunkt) { "Forventer at innsendttidspunkt er satt i tilstand $tilstandType" }
            søknadHendelse.behov(
                Behovtype.ArkiverbarSøknad, "Trenger søknad på et arkiverbart format",
                mapOf(
                    "innsendtTidspunkt" to innsendtTidspunkt.toString()
                )
            )
            // TODO: Emit en hendelse som fører til at vi besvarer faktum i quiz for når søknaden/kravet ble fremsatt
        }

        override fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse, søknad: Søknad) {
            søknad.dokument = arkiverbarSøknadMotattHendelse.dokument()
            søknad.endreTilstand(AvventerMidlertidligJournalføring, arkiverbarSøknadMotattHendelse)
        }
    }

    private object AvventerMidlertidligJournalføring : Tilstand {

        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.AvventerMidlertidligJournalføring

        override fun entering(søknadHendelse: Hendelse, søknad: Søknad) {
            søknad.trengerNyJournalpost(søknadHendelse)
        }

        override fun håndter(
            søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse,
            søknad: Søknad
        ) {
            søknad.journalpostId = søknadMidlertidigJournalførtHendelse.journalpostId()
            søknad.endreTilstand(AvventerJournalføring, søknadMidlertidigJournalførtHendelse)
        }
    }

    private object AvventerJournalføring : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.AvventerJournalføring

        override fun håndter(journalførtHendelse: JournalførtHendelse, søknad: Søknad) {
            // TODO: Legg til sjekk om at det er DENNE søknaden som er journalført.
            søknad.endreTilstand(Journalført, journalførtHendelse)
        }
    }

    private object Journalført : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.Journalført
    }

    fun accept(visitor: SøknadVisitor) {
        visitor.visitSøknad(
            søknadId = søknadId,
            søknadObserver = søknadObserver,
            tilstand = tilstand,
            dokument = dokument,
            journalpostId = journalpostId,
            innsendtTidspunkt = innsendtTidspunkt,
            språk = språk,
            dokumentkrav = dokumentkrav,
            sistEndretAvBruker = sistEndretAvBruker
        )
        tilstand.accept(visitor)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst =
        SpesifikkKontekst(kontekstType = "søknad", mapOf("søknad_uuid" to søknadId.toString()))

    private fun kontekst(hendelse: Hendelse) {
        hendelse.kontekst(this)
        hendelse.kontekst(tilstand)
    }

    data class Dokument(
        val brevkode: String = "NAV 04-01.04",
        val varianter: List<Variant>,
    ) {
        data class Variant(
            val urn: String,
            val format: String,
            val type: String
        )
    }

    private fun trengerNyJournalpost(søknadHendelse: Hendelse) {
        val dokument = requireNotNull(dokument) {
            "Forventet at variabel dokumenter var satt. Er i tilstand: $tilstand"
        }

        søknadHendelse.behov(
            Behovtype.NyJournalpost,
            "Trenger å journalføre søknad",
            mapOf("dokumenter" to listOf(dokument))
        )
    }

    fun deepEquals(other: Any?): Boolean =
        other is Søknad && other.søknadId == this.søknadId &&
            other.tilstand == this.tilstand && other.dokument == this.dokument &&
            other.journalpostId == this.journalpostId && this.dokumentkrav == other.dokumentkrav

    private fun endreTilstand(nyTilstand: Tilstand, søknadHendelse: Hendelse) {
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
        søknadObserver.søknadTilstandEndret(
            SøknadObserver.SøknadEndretTilstandEvent(
                søknadId = søknadId,
                gjeldendeTilstand = tilstand.tilstandType,
                forrigeTilstand = forrigeTilstand.tilstandType
            )
        )
    }
}
