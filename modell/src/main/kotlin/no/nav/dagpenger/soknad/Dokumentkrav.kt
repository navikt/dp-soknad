package no.nav.dagpenger.soknad

import de.slub.urn.RFC
import de.slub.urn.URN
import no.nav.dagpenger.soknad.Krav.Companion.aktive
import no.nav.dagpenger.soknad.Krav.Svar.Companion.TOMT_SVAR
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.IKKE_BESVART
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.SEND_NÅ
import no.nav.dagpenger.soknad.hendelse.DokumentKravHendelse
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
        krav.svar.håndter(hendelse.fil)
    }

    fun håndter(slettFil: SlettFil) {
        val krav = hentKrav(slettFil)
        krav.svar.slettfil(slettFil.urn)
    }

    fun aktiveDokumentKrav() = krav.filter(aktive()).toSet()

    fun inAktiveDokumentKrav() = krav.filterNot(aktive()).toSet()

    override fun equals(other: Any?) =
        other is Dokumentkrav && this.krav == other.krav

    override fun hashCode(): Int = 31 * krav.hashCode()
    fun håndter(dokumentasjonIkkeTilgjengelig: DokumentasjonIkkeTilgjengelig) {
        val krav = hentKrav(dokumentasjonIkkeTilgjengelig)
        krav.håndter(dokumentasjonIkkeTilgjengelig)
    }

    private fun hentKrav(hendelse: DokumentKravHendelse) =
        (
            this.aktiveDokumentKrav().find { it.id == hendelse.kravId }
                ?: hendelse.severe("Fant ikke Dokumentasjonskrav id")
            )

    fun accept(dokumentkravVisitor: DokumentkravVisitor) {
        dokumentkravVisitor.preVisitDokumentkrav()
        krav.forEach { it.accept(dokumentkravVisitor) }
        dokumentkravVisitor.postVisitDokumentkrav()
    }
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

    fun accept(dokumentkravVisitor: DokumentkravVisitor) {
        dokumentkravVisitor.visitKrav(this)
    }

    constructor(sannsynliggjøring: Sannsynliggjøring) : this(
        sannsynliggjøring.id,
        TOMT_SVAR,
        sannsynliggjøring,
        KravTilstand.AKTIV
    )

    enum class KravTilstand {
        AKTIV,
        INAKTIV
    }

    data class Svar(
        val filer: MutableSet<Fil> = mutableSetOf(),
        var valg: SvarValg,
        var begrunnelse: String?
    ) {

        fun håndter(fil: Fil) {
            filer.add(fil)
            valg = SEND_NÅ
            begrunnelse = null
        }
        fun slettfil(urn: URN) = filer.removeIf { it.urn == urn }

        companion object {
            val TOMT_SVAR = Svar(filer = mutableSetOf(), valg = IKKE_BESVART, begrunnelse = null)
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
