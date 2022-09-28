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
        return when (this.id) {
            "faktum.avtjent-militaer-sivilforsvar-tjeneste-siste-12-mnd-dokumentasjon" -> Skjemakode.TJENESTEBEVIS
            "faktum.dokument-tjenestepensjon" -> TODO()
            "faktum.dokument-arbeidslos-GFF-hvilken-periode" -> TODO()
            "faktum.dokument-garantilott-GFF-hvilken-periode" -> TODO()
            "faktum.dokument-etterlonn" -> TODO()
            "faktum.dokument-dagpenger-eos-land" -> TODO()
            "faktum.dokument-annen-ytelse" -> TODO()
            "faktum.dokument-okonomiske-goder-tidligere-arbeidsgiver" -> TODO()
            "faktum.dokument-arbeidsavtale" -> TODO()
            "faktum.dokument-helt-eller-delvis-avsluttet-arbeidsforhold" -> TODO()
            "faktum.dokument-timeliste-for-rotasjon" -> TODO()
            "faktum.dokument-brev-fra-bobestyrer-eller-konkursforvalter" -> TODO()
            "faktum.dokument-ny-arbeidsavtale" -> TODO()
            "faktum.dokument-varsel-om-permittering" -> TODO()
            "faktum.dokumentasjon-redusert-helse-kun-deltid" -> TODO()
            "faktum.dokumentasjon-eneansvar-delt-ansvar-barn-under-18-spesielle-behov-kun-deltid" -> TODO()
            "faktum.dokumentasjon-skift-turnus-kun-deltid" -> TODO()
            "faktum.dokumentasjon-annen-situasjon-kun-deltid" -> TODO()
            "faktum.dokumentasjon-kan-ikke-ta-alle-typer-arbeid-kun-deltid" -> TODO()
            "faktum.dokumentasjon-redusert-helse-ikke-jobbe-hele-norge" -> TODO()
            "faktum.dokumentasjon-eneansvar-delt-ansvar-barn-under-18-spesielle-behov-ikke-jobbe-hele-norge" -> TODO()
            "faktum.dokumentasjon-skift-turnus-ikke-jobbe-hele-norge" -> TODO()
            "faktum.dokumentasjon-annen-situasjon-ikke-jobbe-hele-norge" -> TODO()
            "faktum.dokumentasjon-kan-ikke-ta-alle-typer-arbeid-ikke-jobbe-hele-norge" -> TODO()
            "faktum.dokument-utdanning-sluttdato" -> TODO()
            else -> Skjemakode.ANNET

        }.verdi()
    }

    /**
     *
     *
    "faktum.dokument-tjenestepensjon"
    "faktum.dokument-arbeidslos-GFF-hvilken-periode"
    "faktum.dokument-garantilott-GFF-hvilken-periode"
    "faktum.dokument-etterlonn"
    "faktum.dokument-dagpenger-eos-land"
    "faktum.dokument-annen-ytelse"
    "faktum.dokument-okonomiske-goder-tidligere-arbeidsgiver"
    "faktum.dokument-arbeidsavtale"
    "faktum.dokument-helt-eller-delvis-avsluttet-arbeidsforhold"
    "faktum.dokument-timeliste-for-rotasjon"
    "faktum.dokument-brev-fra-bobestyrer-eller-konkursforvalter"
    "faktum.dokument-ny-arbeidsavtale"
    "faktum.dokument-varsel-om-permittering"
    "faktum.dokumentasjon-redusert-helse-kun-deltid"
    "faktum.dokumentasjon-eneansvar-delt-ansvar-barn-under-18-spesielle-behov-kun-deltid"
    "faktum.dokumentasjon-skift-turnus-kun-deltid"
    "faktum.dokumentasjon-annen-situasjon-kun-deltid"
    "faktum.dokumentasjon-kan-ikke-ta-alle-typer-arbeid-kun-deltid"
    "faktum.dokumentasjon-redusert-helse-ikke-jobbe-hele-norge"
    "faktum.dokumentasjon-eneansvar-delt-ansvar-barn-under-18-spesielle-behov-ikke-jobbe-hele-norge"
    "faktum.dokumentasjon-skift-turnus-ikke-jobbe-hele-norge"
    "faktum.dokumentasjon-annen-situasjon-ikke-jobbe-hele-norge"
    "faktum.dokumentasjon-kan-ikke-ta-alle-typer-arbeid-ikke-jobbe-hele-norge"
    "faktum.dokument-utdanning-sluttdato"
     */
    private enum class Skjemakode(private val skjemakodeverdi: String) {
        TJENESTEBEVIS("T3"),
        ARBEIDSAVTALTE("O2"),
        TIMELISTER("M6"),
        BREV_FRA_BOSTYRE_KONKURSFORVALTER("M7"),
        KOPI_ARBEIDSAVTALE_SLUTTÅRSAK("S7"),
        DOKUMENTASJON_AV_SLUTTÅRSAK("S6"),
        BEKREFTELSE_FRA_STUDIESTED_SKOLE("O9"),
        KOPI_AV_SØKNAD("N2"),
        KOPI_AV_UNDERSØKELSERESULTAT("N5"),
        FØDSELSATTEST_BOSTEDSBEVIS_BARN_UNDER_18_ÅR("X8"),
        SED_U006_FAMILEINFORMASJON("T5"),
        OPPHOLDS_OG_ARBEIDSTILLATELSE_ELLER_REGISTRERINGSBEVIS_FOR_EØS_BORGER("T4"),
        DOKUMENTASJON_AV_SLUTTDATO("T2"),
        ELEVDOKUMENTASJON_FRA_LÆRERSTED("T1"),
        KOPI_AV_SLUTTAVTALE("V6"),
        U1_PERIODER_AV_BETYDNING_FOR_RETTET_TIL_DAGPENGER("U1"),
        SJØFARTSBOK_HYREAVREGNING("S8"),
        ANNET("N6")

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
