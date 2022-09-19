package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.SøknadObserver.SøknadSlettetEvent
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.BrevkodeMottattHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
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
    private val ident: String,
    private var tilstand: Tilstand,
    private var innsending: Innsending?,
    private val språk: Språk,
    private val dokumentkrav: Dokumentkrav,
    private var sistEndretAvBruker: ZonedDateTime?,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : Aktivitetskontekst {
    private val observers = mutableListOf<SøknadObserver>()

    constructor(søknadId: UUID, språk: Språk, ident: String) : this(
        søknadId = søknadId,
        ident = ident,
        tilstand = UnderOpprettelse,
        innsending = null,
        språk = språk,
        dokumentkrav = Dokumentkrav(),
        sistEndretAvBruker = null
    )

    companion object {
        fun rehydrer(
            søknadId: UUID,
            ident: String,
            språk: Språk,
            dokumentkrav: Dokumentkrav,
            sistEndretAvBruker: ZonedDateTime?,
            tilstandsType: Tilstand.Type,
            aktivitetslogg: Aktivitetslogg
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
                tilstand = tilstand,
                innsending = null,
                språk = språk,
                dokumentkrav = dokumentkrav,
                sistEndretAvBruker = sistEndretAvBruker,
                aktivitetslogg = aktivitetslogg
            )
        }
    }

    fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        kontekst(ønskeOmNySøknadHendelse)
        ønskeOmNySøknadHendelse.info("Ønske om søknad registrert")
        tilstand.håndter(ønskeOmNySøknadHendelse, this)
    }

    fun håndter(ønskeOmNyInnsendingHendelse: ØnskeOmNyInnsendingHendelse) {
        kontekst(ønskeOmNyInnsendingHendelse)
        ønskeOmNyInnsendingHendelse.info("Ønske om innsending registrert")
        tilstand.håndter(ønskeOmNyInnsendingHendelse, this)
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

    fun håndter(brevkodeMottattHendelse: BrevkodeMottattHendelse) {
        kontekst(brevkodeMottattHendelse)
        brevkodeMottattHendelse.info("Brevkode mottatt")
        tilstand.håndter(brevkodeMottattHendelse, this)
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
        // @todo : Delegere til tilstand - tilstand burde si om det er ok å lagre
        if (tilstand == Påbegynt) {
            sistEndretAvBruker = ZonedDateTime.now()
            tilstand.håndter(faktumOppdatertHendelse, this)
        } else {
            faktumOppdatertHendelse.severe("Kan ikke oppdatere faktum for søknader i tilstand ${tilstand.tilstandType.name}")
        }
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

    fun addObserver(søknadObserver: SøknadObserver) {
        observers.add(søknadObserver)
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

        fun håndter(brevkodeMottattHendelse: BrevkodeMottattHendelse, søknad: Søknad) =
            brevkodeMottattHendelse.`kan ikke håndteres i denne tilstanden`()

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

        fun håndter(hendelse: DokumentKravSammenstilling, søknad: Søknad) {
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
            if (!søknad.dokumentkrav.ferdigBesvart()) {
                // @todo: Oversette validringsfeil til frontend. Mulig lage et eller annet som frontend kan tolke
                søknadInnsendtHendelse.severe("Alle dokumentkrav må være besvart")
            }
            val innsending = Innsending.ny(
                søknadInnsendtHendelse.innsendtidspunkt(),
                // TODO: Klassifisering til NAV skjemakode
                dokumentkrav = søknad.dokumentkrav
            )
            søknad.innsending = innsending.also {
                it.håndter(søknadInnsendtHendelse)
            }
            søknad.endreTilstand(Innsendt, søknadInnsendtHendelse)
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

        override fun håndter(dokumentKravSammenstilling: DokumentKravSammenstilling, søknad: Søknad) {
            søknad.dokumentkrav.håndter(dokumentKravSammenstilling)
        }
    }

    private object Innsendt : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.Innsendt

        override fun håndter(hendelse: BrevkodeMottattHendelse, søknad: Søknad) {
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
            innsending(søknad).ettersend(søknadInnsendtHendelse, søknad.dokumentkrav)
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

    fun accept(visitor: SøknadVisitor) {
        visitor.visitSøknad(
            søknadId = søknadId,
            ident = ident,
            tilstand = tilstand,
            språk = språk,
            dokumentkrav = dokumentkrav,
            sistEndretAvBruker = sistEndretAvBruker
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

    data class Journalpost(
        val brevkode: String = "NAV 04-01.04",
        val varianter: List<Variant>
    ) {
        data class Variant(
            val urn: String,
            val format: String,
            val type: String
        )
    }

    fun deepEquals(other: Any?): Boolean =
        other is Søknad && other.søknadId == this.søknadId &&
            other.tilstand == this.tilstand && this.dokumentkrav == other.dokumentkrav && this.innsending == other.innsending

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
