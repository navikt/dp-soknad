package no.nav.dagpenger.soknad

class Sannsynliggjøring(
    val id: String,
    private val faktum: Faktum,
    private val sannsynliggjør: MutableSet<Faktum>
) {
    constructor(id: String, faktum: Faktum) : this(id, faktum, mutableSetOf())
    fun sannsynliggjør(faktum: Faktum) = sannsynliggjør.add(faktum)
    fun sannsynliggjør() = sannsynliggjør.toSet()
    internal fun faktum() = faktum
    override fun equals(other: Any?): Boolean = other is Sannsynliggjøring && equals(other)
    private fun equals(other: Sannsynliggjøring) =
        this.id == other.id && this.faktum == other.faktum && this.sannsynliggjør == other.sannsynliggjør

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + faktum.hashCode()
        result = 31 * result + sannsynliggjør.hashCode()
        return result
    }

    override fun toString(): String {
        return "Sannsynliggjøring(id='$id')"
    }
}
