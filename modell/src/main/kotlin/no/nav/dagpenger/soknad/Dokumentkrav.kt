package no.nav.dagpenger.soknad

class Dokumentkrav internal constructor(
    private val sannsynliggjøringer: MutableSet<Sannsynliggjøring> = mutableSetOf(),
    private val krav: MutableSet<Krav> = mutableSetOf()
) {

    constructor() : this(mutableSetOf(), mutableSetOf())

    companion object {
        fun rehydrer(sannsynliggjøringer: Set<Sannsynliggjøring>, krav: Set<Krav>) = Dokumentkrav(sannsynliggjøringer.toMutableSet(), krav.toMutableSet())
    }

    fun håndter(nyeSannsynliggjøringer: Set<Sannsynliggjøring>) {
        val fjernet = this.sannsynliggjøringer.subtract(nyeSannsynliggjøringer)
        this.sannsynliggjøringer.removeAll(fjernet)
        this.sannsynliggjøringer.addAll(nyeSannsynliggjøringer)
        this.oppdaterKrav()
    }

    fun aktiveDokumentKrav() = krav.filter(aktive()).toSet()
    fun inAktiveDokumentKrav() = krav.filterNot(aktive()).toSet()
    fun sannsynliggjøringer() = sannsynliggjøringer.toSet()

    private fun oppdaterKrav() {
        sannsynliggjøringer.forEach { sannsynliggjøring ->
            if (krav.find { it.id == sannsynliggjøring.id } == null) {
                krav.add(
                    Krav(
                        sannsynliggjøring
                    )
                )
            }
        }
    }

    private fun aktive(): (Krav) -> Boolean =
        { sannsynliggjøringer.any { sannsynliggjøring -> sannsynliggjøring.id == it.id } }

    override fun equals(other: Any?) =
        other is Dokumentkrav && this.sannsynliggjøringer == other.sannsynliggjøringer && this.krav == other.krav

    override fun hashCode(): Int {
        var result = sannsynliggjøringer.hashCode()
        result = 31 * result + krav.hashCode()
        return result
    }
}

data class Krav(
    val id: String,
    val beskrivendeId: String,
    val fakta: Set<Faktum>,
    val filer: Set<String> = emptySet()
) {
    constructor(sannsynliggjøring: Sannsynliggjøring) : this(
        sannsynliggjøring.id,
        sannsynliggjøring.faktum().beskrivendeId,
        sannsynliggjøring.sannsynliggjør()
    )
}
