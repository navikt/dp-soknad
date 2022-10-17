package no.nav.dagpenger.soknad

import de.slub.urn.RFC
import de.slub.urn.URN
import no.nav.dagpenger.soknad.Krav.Companion.aktive
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.IKKE_BESVART
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.SEND_NÅ
import no.nav.dagpenger.soknad.hendelse.DokumentKravHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import java.time.ZonedDateTime

class Dokumentkrav private constructor(
    private val krav: MutableSet<Krav> = mutableSetOf()
) {
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
            }
        )
    }

    fun håndter(hendelse: LeggTilFil) {
        val krav = hentKrav(hendelse)
        krav.svar.håndter(hendelse)
    }

    fun håndter(hendelse: SlettFil) {
        val krav = hentKrav(hendelse)
        krav.svar.håndter(hendelse)
    }

    fun håndter(hendelse: DokumentasjonIkkeTilgjengelig) {
        val krav = hentKrav(hendelse)
        krav.svar.håndter(hendelse)
    }

    internal fun tilDokument() =
        aktiveDokumentKrav().filter { it.besvart() }.filter { it.svar.valg == SEND_NÅ }.map { krav ->
            Innsending.Dokument(
                brevkode = krav.tilSkjemakode(),
                varianter = listOf(
                    Innsending.Dokument.Dokumentvariant(
                        filnavn = krav.beskrivendeId,
                        urn = krav.svar.bundle.toString(),
                        variant = "ARKIV", // TODO: hent filtype fra bundle
                        type = "PDF" // TODO: Hva setter vi her?
                    )
                )
            )
        }

    fun aktiveDokumentKrav() = krav.filter(aktive()).toSet()

    fun inAktiveDokumentKrav() = krav.filterNot(aktive()).toSet()

    override fun equals(other: Any?) =
        other is Dokumentkrav && this.krav == other.krav

    override fun hashCode(): Int = 31 * krav.hashCode()

    private fun hentKrav(hendelse: DokumentKravHendelse) =
        (
            this.aktiveDokumentKrav().find { krav -> krav.id == hendelse.kravId }
                ?: hendelse.severe("Fant ikke Dokumentasjonskrav id")
            )

    fun håndter(hendelse: DokumentKravSammenstilling) {
        val krav = hentKrav(hendelse)
        krav.håndter(hendelse)
    }

    fun accept(dokumentkravVisitor: DokumentkravVisitor) {
        dokumentkravVisitor.preVisitDokumentkrav()
        krav.forEach { it.accept(dokumentkravVisitor) }
        dokumentkravVisitor.postVisitDokumentkrav()
    }

    fun ferdigstilt(): Boolean = aktiveDokumentKrav().all { it.ferdigStilt() }
    fun ferdigBesvart() = aktiveDokumentKrav().all { it.besvart() }
    fun ingen() = krav.isEmpty()
}

data class Krav(
    val id: String,
    val svar: Svar,
    val sannsynliggjøring: Sannsynliggjøring,
    var tilstand: KravTilstand
) {
    companion object {
        fun aktive(): (Krav) -> Boolean = { it.tilstand == KravTilstand.AKTIV }
    }

    constructor(sannsynliggjøring: Sannsynliggjøring) : this(
        sannsynliggjøring.id,
        Svar(),
        sannsynliggjøring,
        KravTilstand.AKTIV
    )

    val beskrivendeId: String get() = sannsynliggjøring.faktum().beskrivendeId
    val fakta: Set<Faktum> get() = sannsynliggjøring.sannsynliggjør()
    val beskrivelse: String?
        get() = beskrivendeFaktum()?.let { beskrivendeFaktum ->
            fakta.find { it.beskrivendeId == beskrivendeFaktum }?.svar
        }

    private fun beskrivendeFaktum() = when (this.beskrivendeId) {
        "faktum.dokument-arbeidsavtale" -> "faktum.arbeidsforhold.navn-bedrift"
        "faktum.dokument-dokumentasjon-av-arbeidsforhold" -> "faktum.arbeidsforhold.navn-bedrift"
        else -> null
    }

    fun håndter(nySannsynliggjøringer: Set<Sannsynliggjøring>): Boolean =
        nySannsynliggjøringer.contains(this.sannsynliggjøring).also {
            this.tilstand = when (it) {
                true -> KravTilstand.AKTIV
                false -> KravTilstand.INAKTIV
            }
        }

    fun håndter(hendelse: DokumentKravSammenstilling) {
        this.svar.bundle = hendelse.urn()
    }

    fun accept(dokumentkravVisitor: DokumentkravVisitor) {
        dokumentkravVisitor.visitKrav(this)
    }

    fun besvart() = this.svar.besvart()
    fun ferdigStilt() = this.svar.ferdigStilt()
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
            "faktum.dokument-arbeidsavtale" -> Skjemakode.ARBEIDSAVTALTE
            "faktum.dokument-dokumentasjon-av-arbeidsforhold" -> Skjemakode.DOKUMENTASJON_AV_ARBEIDSFORHOLD
            "faktum.dokument-timelister" -> Skjemakode.TIMELISTER
            "faktum.dokument-brev-fra-bobestyrer-eller-konkursforvalter" -> Skjemakode.BREV_FRA_BOSTYRE_KONKURSFORVALTER
            "faktum.dokument-ny-arbeidsavtale" -> Skjemakode.ARBEIDSAVTALTE
            "faktum.dokument-permitteringsvarsel" -> Skjemakode.PERMITTERINGSVARSEL
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
        ARBEIDSAVTALTE("O2"),
        TIMELISTER("M6"),
        BREV_FRA_BOSTYRE_KONKURSFORVALTER("M7"),
        DOKUMENTASJON_AV_SLUTTDATO("T2"),
        KOPI_AV_SLUTTAVTALE("V6"),
        DOKUMENTASJON_AV_ANDRE_YTELSER("K1"), // @todo: mangler i dp-mottak?
        PERMITTERINGSVARSEL("T6"), // @todo: mangler i dp-mottak?
        DOKUMENTASJON_AV_ARBEIDSFORHOLD("T8"), // @todo: mangler i dp-mottak?
        DOKUMENTASJON_AV_HELSE_OG_FUNKSJONSNIVÅ("T9"), // @todo: mangler i dp-mottak?
        UTTALSE_ELLER_VURDERING_FRA_KOMPETENT_FAGPERSONELL("Y2"), // @todo: mangler i dp-mottak?
        FODSELSATTEST_BOSTEDSBEVIS_BARN_UNDER_18("X8"),
        ANNET("N6");

        fun verdi() = skjemakodeverdi
    }

    enum class KravTilstand {
        AKTIV,
        INAKTIV
    }

    data class Svar(
        val filer: MutableSet<Fil> = mutableSetOf(),
        var valg: SvarValg,
        var begrunnelse: String?,
        var bundle: URN?
    ) {
        internal constructor() : this(filer = mutableSetOf(), valg = IKKE_BESVART, begrunnelse = null, bundle = null)

        fun håndter(hendelse: LeggTilFil) {
            filer.add(hendelse.fil)
            valg = SEND_NÅ
            begrunnelse = null
        }

        fun håndter(hendelse: SlettFil) {
            filer.removeIf { it.urn == hendelse.urn }
        }

        fun håndter(hendelse: DokumentasjonIkkeTilgjengelig) {
            valg = hendelse.valg
            begrunnelse = hendelse.begrunnelse
        }

        fun besvart() = TilstandStrategy.strategy(this).besvart(this)
        fun ferdigStilt() = TilstandStrategy.strategy(this).ferdigStilt(this)

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
            fun ferdigStilt(svar: Svar): Boolean

            object FilSvar : TilstandStrategy {
                override fun besvart(svar: Svar): Boolean = svar.filer.size > 0 && svar.bundle != null
                override fun ferdigStilt(svar: Svar): Boolean = besvart(svar) && svar.bundle != null
            }

            object IngenSvar : TilstandStrategy {
                override fun besvart(svar: Svar): Boolean = false
                override fun ferdigStilt(svar: Svar): Boolean = false
            }

            object AnnetSvar : TilstandStrategy {
                override fun besvart(svar: Svar): Boolean = svar.begrunnelse != null
                override fun ferdigStilt(svar: Svar): Boolean = besvart(svar)
            }
        }

        enum class SvarValg {
            IKKE_BESVART,
            SEND_NÅ,
            SEND_SENERE,
            ANDRE_SENDER,
            SEND_TIDLIGERE,
            SENDER_IKKE
        }
    }

    data class Fil(
        val filnavn: String,
        val urn: URN,
        val storrelse: Long,
        val tidspunkt: ZonedDateTime
    ) {
        init {
            require(urn.supports(RFC.RFC_8141)) {
                "Must support ${RFC.RFC_8141}. See ${RFC.RFC_8141.url()}"
            }
        }
    }
}
