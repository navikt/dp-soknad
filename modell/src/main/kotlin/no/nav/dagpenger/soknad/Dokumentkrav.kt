package no.nav.dagpenger.soknad

import de.slub.urn.RFC
import de.slub.urn.URN
import no.nav.dagpenger.soknad.DokumentkravObserver.DokumentkravInnsendtEvent
import no.nav.dagpenger.soknad.DokumentkravObserver.DokumentkravInnsendtEvent.DokumentkravInnsendt
import no.nav.dagpenger.soknad.Innsending.Dokument
import no.nav.dagpenger.soknad.Krav.Companion.aktive
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.IKKE_BESVART
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.SEND_NÅ
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import java.time.ZonedDateTime

class Dokumentkrav private constructor(
    private val krav: MutableSet<Krav> = mutableSetOf(),
) {
    private val observers = mutableListOf<DokumentkravObserver>()

    constructor() : this(mutableSetOf())

    companion object {
        fun rehydrer(krav: Set<Krav>) = Dokumentkrav(krav.toMutableSet())
    }

    fun håndter(nyeSannsynliggjøringer: Set<Sannsynliggjøring>) {
        val håndterteSannsynliggjøringer = krav.map { it.sannsynliggjøring }.toSet()
        krav.forEach { it.håndter(nyeSannsynliggjøringer) }
        val ikkeHåndterte = nyeSannsynliggjøringer - håndterteSannsynliggjøringer
        krav.addAll(
            ikkeHåndterte.map {
                Krav(it)
            },
        )
    }

    fun håndter(hendelse: SøknadInnsendtHendelse) {
        val aktiveDokumentKrav = aktiveDokumentKrav()
        aktiveDokumentKrav.forEach {
            it.svar.håndter(hendelse)
        }
        val event = DokumentkravInnsendtEvent(
            søknadId = hendelse.søknadID(),
            ident = hendelse.ident(),
            innsendttidspunkt = hendelse.innsendtidspunkt().toLocalDateTime(),
            ferdigBesvart = ferdigBesvart(),
            dokumentkrav = aktiveDokumentKrav.map {
                DokumentkravInnsendt(
                    dokumentnavn = it.beskrivendeId,
                    skjemakode = it.tilSkjemakode(),
                    valg = it.svar.valg.name,
                )
            },
        )
        observers.forEach { it.dokumentkravInnsendt(event) }
    }

    fun addObserver(dokumentkravObserver: DokumentkravObserver) {
        observers.add(dokumentkravObserver)
    }

    internal fun tilDokument(): List<Dokument> =
        aktiveDokumentKrav().filterNot { it.innsendt() }.filter { it.besvart() }.filter { it.svar.valg == SEND_NÅ }
            .map { krav ->
                Dokument(
                    kravId = krav.id,
                    skjemakode = krav.tilSkjemakode(),
                    varianter = listOf(
                        Dokument.Dokumentvariant(
                            filnavn = krav.beskrivendeId,
                            urn = krav.svar.bundle.toString(),
                            variant = "ARKIV", // TODO: hent filtype fra bundle
                            type = "PDF", // TODO: Hva setter vi her?
                        ),
                    ),
                )
            }

    fun aktiveDokumentKrav() = krav.filter(aktive()).toSet()

    fun inAktiveDokumentKrav() = krav.filterNot(aktive()).toSet()

    override fun equals(other: Any?) =
        other is Dokumentkrav && this.krav == other.krav

    override fun hashCode(): Int = 31 * krav.hashCode()

    fun accept(dokumentkravVisitor: DokumentkravVisitor) = krav.forEach { it.accept(dokumentkravVisitor) }

    fun ferdigBesvart() = aktiveDokumentKrav().all { it.besvart() }
    fun ingen() = krav.isEmpty()
}

data class Krav(
    val id: String,
    val svar: Svar,
    val sannsynliggjøring: Sannsynliggjøring,
    var tilstand: KravTilstand,
) {
    companion object {
        fun aktive(): (Krav) -> Boolean = { it.tilstand == KravTilstand.AKTIV }
    }

    constructor(sannsynliggjøring: Sannsynliggjøring) : this(
        sannsynliggjøring.id,
        Svar(),
        sannsynliggjøring,
        KravTilstand.AKTIV,
    )

    val beskrivendeId: String get() = sannsynliggjøring.faktum().beskrivendeId
    val fakta: Set<Faktum> get() = sannsynliggjøring.sannsynliggjør()
    val beskrivelse: String? get() = sannsynliggjøring.faktum().generertAv

    fun håndter(nySannsynliggjøringer: Set<Sannsynliggjøring>): Boolean =
        nySannsynliggjøringer.contains(this.sannsynliggjøring).also {
            this.tilstand = when (it) {
                true -> KravTilstand.AKTIV
                false -> KravTilstand.INAKTIV
            }
        }

    fun accept(dokumentkravVisitor: DokumentkravVisitor) {
        dokumentkravVisitor.visitKrav(this)
    }

    fun besvart() = this.svar.besvart()
    fun innsendt() = this.svar.innsendt

    internal fun tilSkjemakode(): String {
        return when (this.beskrivendeId) {
            "faktum.dokument-avtjent-militaer-sivilforsvar-tjeneste-siste-12-mnd-dokumentasjon" -> Skjemakode.TJENESTEBEVIS
            "faktum.dokument-tjenestepensjon" -> Skjemakode.DOKUMENTASJON_AV_ANDRE_YTELSER
            "faktum.dokument-arbeidslos-GFF-hvilken-periode" -> Skjemakode.DOKUMENTASJON_AV_ANDRE_YTELSER
            "faktum.dokument-garantilott-GFF-hvilken-periode" -> Skjemakode.DOKUMENTASJON_AV_ANDRE_YTELSER
            "faktum.dokument-etterlonn" -> Skjemakode.DOKUMENTASJON_AV_ANDRE_YTELSER
            "faktum.dokument-dagpenger-eos-land" -> Skjemakode.DOKUMENTASJON_AV_ANDRE_YTELSER
            "faktum.dokument-annen-ytelse" -> Skjemakode.DOKUMENTASJON_AV_ANDRE_YTELSER
            "faktum.dokument-okonomiske-goder-tidligere-arbeidsgiver" -> Skjemakode.KOPI_AV_SLUTTAVTALE
            "faktum.dokument-arbeidsavtale" -> Skjemakode.ARBEIDSAVTALE
            "faktum.dokument-arbeidsforhold-avskjediget" -> Skjemakode.DOKUMENTASJON_AV_ARBEIDSFORHOLD
            "faktum.dokument-arbeidsforhold-blitt-sagt-opp" -> Skjemakode.DOKUMENTASJON_AV_ARBEIDSFORHOLD
            "faktum.dokument-arbeidsforhold-sagt-opp-selv" -> Skjemakode.DOKUMENTASJON_AV_ARBEIDSFORHOLD
            "faktum.dokument-arbeidsforhold-redusert" -> Skjemakode.DOKUMENTASJON_AV_ARBEIDSFORHOLD
            "faktum.dokument-timelister" -> Skjemakode.TIMELISTER
            "faktum.dokument-brev-fra-bobestyrer-eller-konkursforvalter" -> Skjemakode.BREV_FRA_BOSTYRE_KONKURSFORVALTER
            "faktum.dokument-arbeidsforhold-permittert" -> Skjemakode.PERMITTERINGSVARSEL
            "faktum.dokument-utdanning-sluttdato" -> Skjemakode.DOKUMENTASJON_AV_SLUTTDATO
            "faktum.dokument-bekreftelse-fra-lege-eller-annen-behandler" -> Skjemakode.DOKUMENTASJON_AV_HELSE_OG_FUNKSJONSNIVÅ
            "faktum.dokument-fulltid-bekreftelse-fra-relevant-fagpersonell" -> Skjemakode.UTTALSE_ELLER_VURDERING_FRA_KOMPETENT_FAGPERSONELL
            "faktum.dokument-hele-norge-bekreftelse-fra-relevant-fagpersonell" -> Skjemakode.UTTALSE_ELLER_VURDERING_FRA_KOMPETENT_FAGPERSONELL
            "faktum.dokument-alle-typer-bekreftelse-fra-relevant-fagpersonell" -> Skjemakode.UTTALSE_ELLER_VURDERING_FRA_KOMPETENT_FAGPERSONELL
            "faktum.dokument-foedselsattest-bostedsbevis-for-barn-under-18aar" -> Skjemakode.FODSELSATTEST_BOSTEDSBEVIS_BARN_UNDER_18
            else -> Skjemakode.ANNET
        }.verdi()
    }

    private enum class Skjemakode(private val skjemakodeverdi: String) {
        TJENESTEBEVIS("T3"),
        ARBEIDSAVTALE("O2"),
        TIMELISTER("M6"),
        BREV_FRA_BOSTYRE_KONKURSFORVALTER("M7"),
        DOKUMENTASJON_AV_SLUTTDATO("T2"),
        KOPI_AV_SLUTTAVTALE("V6"),
        DOKUMENTASJON_AV_ANDRE_YTELSER("K1"),
        PERMITTERINGSVARSEL("T6"),
        DOKUMENTASJON_AV_ARBEIDSFORHOLD("T8"),
        DOKUMENTASJON_AV_HELSE_OG_FUNKSJONSNIVÅ("T9"),
        UTTALSE_ELLER_VURDERING_FRA_KOMPETENT_FAGPERSONELL("Y2"),
        FODSELSATTEST_BOSTEDSBEVIS_BARN_UNDER_18("X8"),
        ANNET("N6"),
        ;

        fun verdi() = skjemakodeverdi
    }

    enum class KravTilstand {
        AKTIV,
        INAKTIV,
    }

    data class Svar(
        val filer: MutableSet<Fil> = mutableSetOf(),
        var valg: SvarValg,
        var begrunnelse: String?,
        var bundle: URN?,
        var innsendt: Boolean, // todo slå sammen med bundle?
    ) {
        constructor() : this(
            filer = mutableSetOf(),
            valg = IKKE_BESVART,
            begrunnelse = null,
            bundle = null,
            innsendt = false,
        )

        fun håndter(hendelse: SøknadInnsendtHendelse) {
            innsendt = true
        }
        fun besvart() = TilstandStrategy.strategy(this).besvart(this)

        private sealed interface TilstandStrategy {
            companion object {
                fun strategy(svar: Svar): TilstandStrategy {
                    return when (svar.valg) {
                        IKKE_BESVART -> IngenSvar
                        SEND_NÅ -> FilSvar
                        SvarValg.SEND_SENERE -> AnnetSvar
                        SvarValg.ANDRE_SENDER -> AnnetSvar
                        SvarValg.SEND_TIDLIGERE -> AnnetSvar
                        SvarValg.SENDER_IKKE -> AnnetSvar
                    }
                }
            }

            fun besvart(svar: Svar): Boolean

            object FilSvar : TilstandStrategy {
                override fun besvart(svar: Svar): Boolean = svar.filer.size > 0 && svar.bundle != null
            }

            object IngenSvar : TilstandStrategy {
                override fun besvart(svar: Svar): Boolean = false
            }

            object AnnetSvar : TilstandStrategy {
                override fun besvart(svar: Svar): Boolean = svar.begrunnelse != null
            }
        }

        enum class SvarValg {
            IKKE_BESVART,
            SEND_NÅ,
            SEND_SENERE,
            ANDRE_SENDER,
            SEND_TIDLIGERE,
            SENDER_IKKE,
        }
    }

    data class Fil(
        val filnavn: String,
        val urn: URN,
        val storrelse: Long,
        val tidspunkt: ZonedDateTime,
        var bundlet: Boolean,
    ) {
        init {
            require(urn.supports(RFC.RFC_8141)) {
                "Must support ${RFC.RFC_8141}. See ${RFC.RFC_8141.url()}"
            }
        }
    }
}
