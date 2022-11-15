package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.SøknadObserver.SøknadSlettetEvent
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.HarPåbegyntSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.InnsendingMetadataMottattHendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import java.time.ZonedDateTime
import java.util.UUID

class Søknad private constructor(
    private val søknadId: UUID,
    private val ident: String,
    private val opprettet: ZonedDateTime,
    private var tilstand: Tilstand,
    private var innsending: NyInnsending?,
    private val språk: Språk,
    private val dokumentkrav: Dokumentkrav,
    private var sistEndretAvBruker: ZonedDateTime,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
    private var prosessversjon: Prosessversjon?
) : Aktivitetskontekst, InnsendingObserver {
    private val observers = mutableListOf<SøknadObserver>()

    fun søknadUUID() = søknadId
    fun ident() = ident

    init {
        innsending?.addObserver(this)
    }

    constructor(søknadId: UUID, språk: Språk, ident: String) : this(
        søknadId = søknadId,
        ident = ident,
        opprettet = ZonedDateTime.now(),
        tilstand = UnderOpprettelse,
        innsending = null,
        språk = språk,
        dokumentkrav = Dokumentkrav(),
        sistEndretAvBruker = ZonedDateTime.now(),
        prosessversjon = null
    )

    companion object {
        fun rehydrer(
            søknadId: UUID,
            ident: String,
            opprettet: ZonedDateTime,
            språk: Språk,
            dokumentkrav: Dokumentkrav,
            sistEndretAvBruker: ZonedDateTime,
            tilstandsType: Tilstand.Type,
            aktivitetslogg: Aktivitetslogg,
            innsending: NyInnsending?,
            prosessversjon: Prosessversjon?
        ): Søknad {
            val tilstand: Tilstand = when (tilstandsType) {
                Tilstand.Type.UnderOpprettelse -> UnderOpprettelse
                Tilstand.Type.Påbegynt -> Påbegynt
                Tilstand.Type.Innsendt -> Innsendt
                Tilstand.Type.Slettet -> throw IllegalArgumentException("Kan ikke rehydrere slettet søknad med id $søknadId")
            }
            return Søknad(
                søknadId = søknadId,
                ident = ident,
                opprettet = opprettet,
                tilstand = tilstand,
                innsending = innsending,
                språk = språk,
                dokumentkrav = dokumentkrav,
                sistEndretAvBruker = sistEndretAvBruker,
                aktivitetslogg = aktivitetslogg,
                prosessversjon = prosessversjon
            )
        }

        fun Søknad.erPåbegynt() =
            tilstand.tilstandType == Påbegynt.tilstandType || tilstand.tilstandType == UnderOpprettelse.tilstandType

        fun Søknad.erDagpenger(): Boolean = prosessversjon?.prosessnavn?.id == "Dagpenger"
    }

    fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        kontekst(ønskeOmNySøknadHendelse)
        ønskeOmNySøknadHendelse.info("Ønske om søknad registrert")
        tilstand.håndter(ønskeOmNySøknadHendelse, this)
    }

    fun håndter(harPåbegyntSøknadHendelse: HarPåbegyntSøknadHendelse) {
        kontekst(harPåbegyntSøknadHendelse)
        tilstand.håndter(harPåbegyntSøknadHendelse, this)
    }

    fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        kontekst(søknadOpprettetHendelse)
        søknadOpprettetHendelse.info("Oppretter søknad")
        tilstand.håndter(søknadOpprettetHendelse, this)
    }

    fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse) {
        kontekst(søknadInnsendtHendelse)
        søknadInnsendtHendelse.info("Sender inn søknaden")
        sistEndretAvBruker = ZonedDateTime.now()
        tilstand.håndter(søknadInnsendtHendelse, this)
    }

    fun håndter(innsendingMetadataMottattHendelse: InnsendingMetadataMottattHendelse) {
        kontekst(innsendingMetadataMottattHendelse)
        innsendingMetadataMottattHendelse.info("Brevkode mottatt")
        tilstand.håndter(innsendingMetadataMottattHendelse, this)
    }

    fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse) {
        kontekst(arkiverbarSøknadMotattHendelse)
        arkiverbarSøknadMotattHendelse.info("Arkiverbar søknad mottatt")
        if (!arkiverbarSøknadMotattHendelse.valider()) {
            arkiverbarSøknadMotattHendelse.warn("Ikke gyldig dokumentlokasjon")
            return
        }
        tilstand.håndter(arkiverbarSøknadMotattHendelse, this)
    }

    fun håndter(søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse) {
        kontekst(søknadMidlertidigJournalførtHendelse)
        søknadMidlertidigJournalførtHendelse.info("Søknad midlertidig journalført")
        tilstand.håndter(søknadMidlertidigJournalførtHendelse, this)
    }

    fun håndter(journalførtHendelse: JournalførtHendelse) {
        kontekst(journalførtHendelse)
        journalførtHendelse.info("Søknad journalført")
        tilstand.håndter(journalførtHendelse, this)
    }

    fun håndter(faktumOppdatertHendelse: FaktumOppdatertHendelse) {
        kontekst(faktumOppdatertHendelse)
        tilstand.håndter(faktumOppdatertHendelse, this)
    }

    fun håndter(søkeroppgaveHendelse: SøkeroppgaveHendelse) {
        kontekst(søkeroppgaveHendelse)
        søkeroppgaveHendelse.info("Søkeroppgave mottatt")
        tilstand.håndter(søkeroppgaveHendelse, this)
    }

    fun håndter(slettSøknadHendelse: SlettSøknadHendelse) {
        kontekst(slettSøknadHendelse)
        slettSøknadHendelse.info("Forsøker å slette søknad")
        tilstand.håndter(slettSøknadHendelse, this)
    }

    fun håndter(dokumentasjonIkkeTilgjengelig: DokumentasjonIkkeTilgjengelig) {
        kontekst(dokumentasjonIkkeTilgjengelig)
        tilstand.håndter(dokumentasjonIkkeTilgjengelig, this)
    }

    fun håndter(leggTilFil: LeggTilFil) {
        kontekst(leggTilFil)
        leggTilFil.info("Legger til fil")
        tilstand.håndter(leggTilFil, this)
    }

    fun håndter(slettFil: SlettFil) {
        kontekst(slettFil)
        tilstand.håndter(slettFil, this)
    }

    fun håndter(hendelse: DokumentKravSammenstilling) {
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    fun håndter(hendelse: MigrertProsessHendelse) {
        kontekst(hendelse)
        tilstand.håndter(hendelse, this)
    }

    fun addObserver(søknadObserver: SøknadObserver) {
        observers.add(søknadObserver)
    }

    interface Tilstand : Aktivitetskontekst {
        val tilstandType: Type

        fun entering(søknadHendelse: Hendelse, søknad: Søknad) {}

        fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) =
            ønskeOmNySøknadHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(harPåbegyntSøknadHendelse: HarPåbegyntSøknadHendelse, søknad: Søknad) =
            harPåbegyntSøknadHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse, søknad: Søknad) =
            søknadOpprettetHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) =
            søknadInnsendtHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(innsendingMetadataMottattHendelse: InnsendingMetadataMottattHendelse, søknad: Søknad) =
            innsendingMetadataMottattHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(arkiverbarSøknadMotattHendelse: ArkiverbarSøknadMottattHendelse, søknad: Søknad) =
            arkiverbarSøknadMotattHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse, søknad: Søknad) =
            søknadMidlertidigJournalførtHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(journalførtHendelse: JournalførtHendelse, søknad: Søknad) =
            journalførtHendelse.`kan ikke håndteres i denne tilstanden`()

        fun håndter(faktumOppdatertHendelse: FaktumOppdatertHendelse, søknad: Søknad): Unit =
            faktumOppdatertHendelse.severe("Kan ikke oppdatere faktum for søknader i tilstand ${tilstandType.name}")

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

        fun håndter(hendelse: DokumentKravSammenstilling, søknad: Søknad) {
            hendelse.`kan ikke håndteres i denne tilstanden`()
        }

        fun håndter(hendelse: MigrertProsessHendelse, søknad: Søknad) {
            hendelse.`kan ikke håndteres i denne tilstanden`()
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
            Innsendt,
            Slettet
        }
    }

    private object UnderOpprettelse : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.UnderOpprettelse

        override fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) {
            ønskeOmNySøknadHendelse.behov(
                Behovtype.NySøknad,
                "Behov for å starte søknadsprosess",
                mapOf("prosessnavn" to ønskeOmNySøknadHendelse.prosessnavn.id)
            )
        }

        override fun håndter(søknadOpprettetHendelse: SøknadOpprettetHendelse, søknad: Søknad) {
            søknad.prosessversjon = søknadOpprettetHendelse.prosessversjon()
            søknad.endreTilstand(Påbegynt, søknadOpprettetHendelse)
        }
    }

    private object Påbegynt : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.Påbegynt

        override fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) {
            if (!søknad.dokumentkrav.ferdigBesvart()) {
                // @todo: Oversette validringsfeil til frontend. Mulig lage et eller annet som frontend kan tolke
                søknadInnsendtHendelse.severe("Alle dokumentkrav må være besvart")
            }
            val krav = søknad.dokumentkrav.aktiveDokumentKrav()
            val besvarte = krav.filter { it.besvart() }
            val innsendte = besvarte.filter { it.svar.valg == Krav.Svar.SvarValg.SEND_NÅ }
            val innsending = Innsending.ny(
                søknadInnsendtHendelse.innsendtidspunkt(),
                // TODO: Klassifisering til NAV skjemakode
                dokumentkrav = søknad.dokumentkrav
            ).also {
                it.addObserver(søknad)
            }
            søknadInnsendtHendelse.info("Innsending ${innsending.toSpesifikkKontekst()} opprettet med ${krav.size} dokumentkrav, ${besvarte.size} besvarte, ${innsendte.size} innsendte")
            søknad.innsending = innsending.also {
                it.håndter(søknadInnsendtHendelse)
            }
            søknad.dokumentkrav.håndter(søknadInnsendtHendelse)
            søknad.endreTilstand(Innsendt, søknadInnsendtHendelse)
        }

        override fun håndter(faktumOppdatertHendelse: FaktumOppdatertHendelse, søknad: Søknad) {
            søknad.sistEndretAvBruker = ZonedDateTime.now()
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
            søknad.håndter(søkeroppgaveHendelse.sannsynliggjøringer())
        }

        override fun håndter(slettFil: SlettFil, søknad: Søknad) {
            søknad.dokumentkrav.håndter(slettFil)
        }

        override fun håndter(slettSøknadHendelse: SlettSøknadHendelse, søknad: Søknad) {
            søknad.endreTilstand(Slettet, slettSøknadHendelse)
        }

        override fun håndter(dokumentKravSammenstilling: DokumentKravSammenstilling, søknad: Søknad) {
            søknad.dokumentkrav.håndter(dokumentKravSammenstilling)
        }

        override fun håndter(hendelse: MigrertProsessHendelse, søknad: Søknad) {
            val forrigeVersjon = søknad.prosessversjon ?: Prosessversjon("pre-migrering", 0)
            søknad.prosessversjon = hendelse.prosessversjon
            søknad.migrert(søknad.ident, forrigeVersjon)
        }
    }

    private object Innsendt : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.Innsendt

        override fun håndter(hendelse: InnsendingMetadataMottattHendelse, søknad: Søknad) {
            innsending(søknad).håndter(hendelse)
        }

        override fun håndter(hendelse: ArkiverbarSøknadMottattHendelse, søknad: Søknad) {
            innsending(søknad).håndter(hendelse)
        }

        override fun håndter(leggTilFil: LeggTilFil, søknad: Søknad) {
            søknad.dokumentkrav.håndter(leggTilFil)
        }

        override fun håndter(slettFil: SlettFil, søknad: Søknad) {
            søknad.dokumentkrav.håndter(slettFil)
        }

        override fun håndter(dokumentKravSammenstilling: DokumentKravSammenstilling, søknad: Søknad) {
            søknad.dokumentkrav.håndter(dokumentKravSammenstilling)
        }

        override fun håndter(
            hendelse: SøknadMidlertidigJournalførtHendelse,
            søknad: Søknad
        ) {
            innsending(søknad).håndter(hendelse)
        }

        override fun håndter(hendelse: JournalførtHendelse, søknad: Søknad) {
            innsending(søknad).håndter(hendelse)
        }

        override fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) {
            if (søknad.erDagpenger()) {
                innsending(søknad).ettersend(søknadInnsendtHendelse, søknad.dokumentkrav)
                søknad.dokumentkrav.håndter(søknadInnsendtHendelse)
            } else {
                søknadInnsendtHendelse.severe("Kan ikke lage ettersending av prosess ${søknad.prosessversjon?.prosessnavn?.id}")
            }
        }

        private fun innsending(søknad: Søknad) =
            requireNotNull(søknad.innsending) { "Forventet at innsending er laget i tilstand $tilstandType" }
    }

    private object Slettet : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.Slettet

        override fun entering(søknadHendelse: Hendelse, søknad: Søknad) {
            søknad.slettet(søknad.ident)
        }
    }

    private fun håndter(nyeSannsynliggjøringer: Set<Sannsynliggjøring>) {
        this.dokumentkrav.håndter(nyeSannsynliggjøringer)
    }

    private fun slettet(ident: String) {
        observers.forEach {
            it.søknadSlettet(SøknadSlettetEvent(søknadId, ident))
        }
    }

    private fun migrert(ident: String, forrigeProsessversjon: Prosessversjon) {
        val gjeldendeVersjon = requireNotNull(this.prosessversjon) { "Kan ikke migrere søknad uten ny prosessversjon" }
        observers.forEach {
            it.søknadMigrert(
                SøknadObserver.SøknadMigrertEvent(
                    søknadId,
                    ident,
                    forrigeProsessversjon,
                    gjeldendeVersjon
                )
            )
        }
    }

    fun accept(visitor: SøknadVisitor) {
        visitor.visitSøknad(
            søknadId = søknadId,
            ident = ident,
            opprettet = opprettet,
            tilstand = tilstand,
            språk = språk,
            dokumentkrav = dokumentkrav,
            sistEndretAvBruker = sistEndretAvBruker,
            prosessversjon = prosessversjon
        )
        tilstand.accept(visitor)
        aktivitetslogg.accept(visitor)
        dokumentkrav.accept(visitor)
        innsending?.accept(visitor)
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst =
        SpesifikkKontekst(kontekstType = "søknad", mapOf("søknad_uuid" to søknadId.toString(), "ident" to ident))

    private fun kontekst(hendelse: Hendelse) {
        hendelse.kontekst(this)
        hendelse.kontekst(tilstand)
    }

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

    override fun innsendingTilstandEndret(event: InnsendingObserver.InnsendingEndretTilstandEvent) {
        observers.forEach {
            it.innsendingTilstandEndret(
                SøknadObserver.SøknadInnsendingEndretTilstandEvent(
                    søknadId = this.søknadId,
                    innsending = event
                )
            )
        }
    }

    private fun varsleOmEndretTilstand(forrigeTilstand: Tilstand) {
        observers.forEach {
            it.søknadTilstandEndret(
                SøknadObserver.SøknadEndretTilstandEvent(
                    søknadId = søknadId,
                    gjeldendeTilstand = tilstand.tilstandType,
                    forrigeTilstand = forrigeTilstand.tilstandType
                )
            )
        }
    }
}
