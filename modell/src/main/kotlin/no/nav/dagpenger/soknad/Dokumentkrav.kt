package no.nav.dagpenger.soknad

class Dokumentkrav private constructor(
    private val sannsynliggjøringer: MutableSet<Sannsynliggjøring> = mutableSetOf(),
    private val krav: MutableSet<Krav> = mutableSetOf()
) {

    companion object {
        internal val Ingen = Dokumentkrav()
    }

    fun håndter(nyeSannsynliggjøringer: Set<Sannsynliggjøring>) {
        val fjernet = this.sannsynliggjøringer.subtract(nyeSannsynliggjøringer)
        this.sannsynliggjøringer.removeAll(fjernet)
        this.sannsynliggjøringer.addAll(nyeSannsynliggjøringer)
        this.oppdaterDokumentkrav()
    }

    fun accept(dokumentkravVisitor: DokumentkravVisitor) {
        dokumentkravVisitor.visitSannsynliggjøringer(sannsynliggjøringer.toSet())
        dokumentkravVisitor.visitAktiveKrav(
            krav.filter(aktive()).toSet()
        )
        dokumentkravVisitor.visitInaktiveKrav(
            krav.filterNot(aktive()).toSet()
        )
    }

    private fun oppdaterDokumentkrav() {
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
}

data class Krav(
    val id: String,
    private val beskrivendeId: String,
    private val fakta: Set<Faktum>,
    private val filer: Set<String> = emptySet()
) {
    constructor(sannsynliggjøring: Sannsynliggjøring) : this(
        sannsynliggjøring.id,
        sannsynliggjøring.faktum().beskrivendeId,
        sannsynliggjøring.sannsynliggjør()
    )
}
