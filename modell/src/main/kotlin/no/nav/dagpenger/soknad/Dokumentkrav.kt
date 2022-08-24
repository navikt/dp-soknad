package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Krav.Companion.aktive

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

        val ikkeHånderte = nyeSannsynliggjøringer - håndterteSannsynliggjøringer
        krav.addAll(
            ikkeHånderte.map {
                Krav(it)
            }
        )
    }

    fun aktiveDokumentKrav() = krav.filter(aktive()).toSet()

    fun inAktiveDokumentKrav() = krav.filterNot(aktive()).toSet()

    override fun equals(other: Any?) =
        other is Dokumentkrav && this.krav == other.krav

    override fun hashCode(): Int = 31 * krav.hashCode()
}

data class Krav(
    val id: String,
    val filer: Set<String> = emptySet(),
    val sannsynliggjøring: Sannsynliggjøring,
    internal var tilstand: KravTilstand
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

    constructor(sannsynliggjøring: Sannsynliggjøring) : this(
        sannsynliggjøring.id,
        emptySet(),
        sannsynliggjøring,
        KravTilstand.AKTIV
    )

    enum class KravTilstand {
        AKTIV,
        INAKTIV
    }
}
