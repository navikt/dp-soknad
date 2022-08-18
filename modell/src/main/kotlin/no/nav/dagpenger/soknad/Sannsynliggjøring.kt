package no.nav.dagpenger.soknad

class Sannsynliggjøring(
    val id: String,
    private val faktum: Faktum,
    private val sannsynliggjør: MutableSet<Faktum>
) {

    constructor(id: String, faktum: Faktum) : this(id, faktum, mutableSetOf())

    fun sannsynliggjør(faktum: Faktum) = sannsynliggjør.add(faktum)

    fun sannsynliggjør() = sannsynliggjør.toSet()

    override fun toString(): String = "Id={$id}, faktum:{$faktum}, sannsynliggjører={$sannsynliggjør}"
}
