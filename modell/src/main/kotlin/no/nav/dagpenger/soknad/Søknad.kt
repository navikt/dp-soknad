package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Aktivitetslogg.Aktivitet.Behov.Behovtype
import no.nav.dagpenger.soknad.SøknadObserver.SøknadSlettetEvent
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.HarPåbegyntSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import java.time.ZonedDateTime
import java.util.UUID

class Søknad private constructor(
    private val søknadId: UUID,
    private val ident: String,
    private val opprettet: ZonedDateTime,
    private var innsendt: ZonedDateTime?,
    private var tilstand: Tilstand,
    private val språk: Språk,
    private val dokumentkrav: Dokumentkrav,
    private var sistEndretAvBruker: ZonedDateTime,
    internal val aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
    private var prosessversjon: Prosessversjon?,
    private var data: Lazy<SøknadData>,
) : Aktivitetskontekst, DokumentkravObserver {
    private val observers = mutableListOf<SøknadObserver>()

    override fun dokumentkravInnsendt(event: DokumentkravObserver.DokumentkravInnsendtEvent) {
        val prosessnavn = requireNotNull(prosessversjon?.prosessnavn)
        event.søknadType = prosessnavn.id
        event.innsendingstype = when (tilstand) {
            is Påbegynt -> "NyInnsending"
            is Innsendt -> "Ettersending"
            else -> throw IllegalStateException("")
        }
        observers.forEach { it.dokumentkravInnsendt(event) }
    }

    fun søknadUUID() = søknadId
    fun ident() = ident

    constructor(
        søknadId: UUID,
        språk: Språk,
        ident: String,
        data: Lazy<SøknadData> = lazy { throw IllegalStateException("Mangler søknadsdata") },
    ) : this(
        søknadId = søknadId,
        ident = ident,
        opprettet = ZonedDateTime.now(),
        innsendt = null,
        tilstand = UnderOpprettelse,
        språk = språk,
        dokumentkrav = Dokumentkrav(),
        sistEndretAvBruker = ZonedDateTime.now(),
        prosessversjon = null,
        data = data,
    )

    init {
        dokumentkrav.addObserver(this)
    }

    companion object {
        fun rehydrer(
            søknadId: UUID,
            ident: String,
            opprettet: ZonedDateTime,
            innsendt: ZonedDateTime?,
            språk: Språk,
            dokumentkrav: Dokumentkrav,
            sistEndretAvBruker: ZonedDateTime,
            tilstandsType: Tilstand.Type,
            aktivitetslogg: Aktivitetslogg,
            prosessversjon: Prosessversjon?,
            data: Lazy<SøknadData>,
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
                innsendt = innsendt,
                tilstand = tilstand,
                språk = språk,
                dokumentkrav = dokumentkrav,
                sistEndretAvBruker = sistEndretAvBruker,
                aktivitetslogg = aktivitetslogg,
                prosessversjon = prosessversjon,
                data = data,
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

        fun håndter(faktumOppdatertHendelse: FaktumOppdatertHendelse, søknad: Søknad): Unit =
            faktumOppdatertHendelse.severe("Kan ikke oppdatere faktum for søknader i tilstand ${tilstandType.name}")

        fun håndter(søkeroppgaveHendelse: SøkeroppgaveHendelse, søknad: Søknad) {
            søkeroppgaveHendelse.`kan ikke håndteres i denne tilstanden`()
        }

        fun håndter(slettSøknadHendelse: SlettSøknadHendelse, søknad: Søknad) {
            slettSøknadHendelse.severe("Kan ikke slette søknad i tilstand $tilstandType")
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
            Slettet,
        }
    }

    private object UnderOpprettelse : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.UnderOpprettelse

        override fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse, søknad: Søknad) {
            ønskeOmNySøknadHendelse.behov(
                Behovtype.NySøknad,
                "Behov for å starte søknadsprosess",
                mapOf("prosessnavn" to ønskeOmNySøknadHendelse.prosessnavn.id),
            )
        }

        override fun håndter(søkeroppgaveHendelse: SøkeroppgaveHendelse, søknad: Søknad) {
            søkeroppgaveHendelse.håndter(søknad)
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
            if (!søknad.data.value.erFerdig()) {
                // @todo: Oversette validringsfeil til frontend. Mulig lage et eller annet som frontend kan tolke
                søknadInnsendtHendelse.severe("Alle faktum må være besvart")
            }
            if (!søknad.dokumentkrav.ferdigBesvart()) {
                // @todo: Oversette validringsfeil til frontend. Mulig lage et eller annet som frontend kan tolke
                søknadInnsendtHendelse.severe("Alle dokumentkrav må være besvart")
            }
            søknadInnsendtHendelse.behov(
                Behovtype.NyInnsending,
                "Søknad innsendt, trenger ny innsending",
                mapOf(
                    "innsendtTidspunkt" to søknadInnsendtHendelse.innsendtidspunkt(),
                    "dokumentkrav" to søknad.dokumentkrav.tilDokument().map { it.toMap() },
                ),
            )

            søknad.dokumentkrav.håndter(søknadInnsendtHendelse)
            søknad.innsendt(søknadInnsendtHendelse.innsendtidspunkt())
            søknad.endreTilstand(Innsendt, søknadInnsendtHendelse)
        }

        override fun håndter(faktumOppdatertHendelse: FaktumOppdatertHendelse, søknad: Søknad) {
            søknad.sistEndretAvBruker = ZonedDateTime.now()
        }

        override fun håndter(harPåbegyntSøknadHendelse: HarPåbegyntSøknadHendelse, søknad: Søknad) {
        }

        override fun håndter(søkeroppgaveHendelse: SøkeroppgaveHendelse, søknad: Søknad) {
            søkeroppgaveHendelse.håndter(søknad)
        }

        override fun håndter(slettSøknadHendelse: SlettSøknadHendelse, søknad: Søknad) {
            søknad.endreTilstand(Slettet, slettSøknadHendelse)
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

        override fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse, søknad: Søknad) {
            if (!søknad.erDagpenger()) {
                søknadInnsendtHendelse.severe("Kan ikke lage ettersending av prosess ${søknad.prosessversjon?.prosessnavn?.id}")
            }

            søknadInnsendtHendelse.behov(
                Behovtype.NyEttersending,
                "Søknad ettersend, trenger ny ettersending",
                mapOf(
                    "innsendtTidspunkt" to søknadInnsendtHendelse.innsendtidspunkt(),
                    "dokumentkrav" to søknad.dokumentkrav.tilDokument().map { it.toMap() },
                ),
            )

            søknad.dokumentkrav.håndter(søknadInnsendtHendelse)
        }
    }

    private object Slettet : Tilstand {
        override val tilstandType: Tilstand.Type
            get() = Tilstand.Type.Slettet

        override fun entering(søknadHendelse: Hendelse, søknad: Søknad) {
            søknad.slettet(søknad.ident)
        }
    }

    internal fun håndter(nyeSannsynliggjøringer: Set<Sannsynliggjøring>) {
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
                    gjeldendeVersjon,
                ),
            )
        }
    }

    fun accept(visitor: SøknadVisitor) {
        visitor.visitSøknad(
            søknadId = søknadId,
            ident = ident,
            opprettet = opprettet,
            innsendt = innsendt,
            tilstand = tilstand,
            språk = språk,
            dokumentkrav = dokumentkrav,
            sistEndretAvBruker = sistEndretAvBruker,
            prosessversjon = prosessversjon,
        )
        tilstand.accept(visitor)
        aktivitetslogg.accept(visitor)
        dokumentkrav.accept(visitor)
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

    private fun innsendt(innsendtidspunkt: ZonedDateTime) {
        this.innsendt = innsendtidspunkt
        val event = SøknadObserver.SøknadInnsendtEvent(søknadId = søknadId, innsendt = innsendtidspunkt)
        observers.forEach {
            it.søknadInnsendt(event)
        }
    }

    private fun varsleOmEndretTilstand(forrigeTilstand: Tilstand) {
        observers.forEach {
            it.søknadTilstandEndret(
                SøknadObserver.SøknadEndretTilstandEvent(
                    søknadId = søknadId,
                    ident = ident,
                    prosessversjon = prosessversjon,
                    gjeldendeTilstand = tilstand.tilstandType,
                    forrigeTilstand = forrigeTilstand.tilstandType,
                ),
            )
        }
    }
}
