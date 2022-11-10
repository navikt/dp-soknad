package no.nav.dagpenger.soknad

data class Prosessnavn(val id: String)

data class Prosessversjon(val prosessnavn: Prosessnavn, val versjon: Int) {
    constructor(navn: String, versjon: Int) : this(Prosessnavn(navn), versjon)
}
