package no.nav.dagpenger.soknad

class Sannsynliggjøring(
    val id: String,
    private val faktum: Faktum,
    private val sannsynliggjør: MutableList<Faktum>
) {

    constructor(id: String, faktum: Faktum) : this(id, faktum, mutableListOf())

    fun sannsynliggjør(faktum: Faktum) = sannsynliggjør.add(faktum)

    override fun toString(): String = "Id={$id}, sannsynliggjører={$sannsynliggjør}"
}
