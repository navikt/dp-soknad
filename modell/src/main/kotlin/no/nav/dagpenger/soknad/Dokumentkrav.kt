package no.nav.dagpenger.soknad

import de.slub.urn.RFC
import de.slub.urn.URN
import no.nav.dagpenger.soknad.Krav.Companion.aktive
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.IKKE_BESVART
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.SEND_NÅ
import no.nav.dagpenger.soknad.hendelse.DokumentKravHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.DokumentasjonkravFerdigstilt
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
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

    fun håndter(søknadInnsendtHendelse: SøknadInnsendtHendelse) {

        val filer = uKomplette().associate { krav -> krav.id to krav.svar.filer.map { it.urn.toString() } }
        søknadInnsendtHendelse.behov(
            type = Aktivitetslogg.Aktivitet.Behov.Behovtype.Ferdigstill,
            melding = "Trenger å bundle dokumentkrav filer",
            mapOf(
                "krav" to filer
            )
        )
    }

    private fun uKomplette() = aktiveDokumentKrav().filterNot { it.ferdigStilt() }.toSet()

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

    fun håndter(hendelse: DokumentasjonkravFerdigstilt) {
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

    fun håndter(dokumentasjonIkkeTilgjengelig: DokumentasjonIkkeTilgjengelig) {
        this.svar.valg = dokumentasjonIkkeTilgjengelig.valg
        this.svar.begrunnelse = dokumentasjonIkkeTilgjengelig.begrunnelse
    }

    fun håndter(hendelse: DokumentasjonkravFerdigstilt) {
        this.svar.bundle = Fil(
            "ferdigstilt.pdf",
            hendelse.ferdigstiltURN,
            0,
            ZonedDateTime.now()

        )
        // @todo: Ta var på ferdigstilt urn og besvare quiz med urn'en
    }

    fun accept(dokumentkravVisitor: DokumentkravVisitor) {
        dokumentkravVisitor.visitKrav(this)
    }

    fun besvart() = this.svar.besvart()
    fun ferdigStilt() = this.svar.ferdigStilt()

    enum class KravTilstand {
        AKTIV,
        INAKTIV
    }

    data class Svar(
        val filer: MutableSet<Fil> = mutableSetOf(),
        var valg: SvarValg,
        var begrunnelse: String?,
        var bundle: Fil?
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
                override fun besvart(svar: Svar): Boolean = svar.filer.size > 0
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
