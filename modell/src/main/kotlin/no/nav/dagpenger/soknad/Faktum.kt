package no.nav.dagpenger.soknad

data class Faktum(
    val id: String,
    val beskrivendeId: String,
    val type: String,
    val roller: List<String>,
    val sannsynliggjøresAv: List<Faktum>,
    val svar: Any?
)
