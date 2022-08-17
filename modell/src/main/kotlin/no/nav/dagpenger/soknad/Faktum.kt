package no.nav.dagpenger.soknad

data class Faktum(
    var id: String,
    val beskrivendeId: String,
    val type: String,
    val roller: List<String>,
    val sannsynliggjøresAv: List<Faktum>,
    val readOnly: Boolean,
    val svar: Any?
)
