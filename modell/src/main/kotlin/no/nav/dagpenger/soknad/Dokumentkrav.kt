package no.nav.dagpenger.soknad

/*

1. flytte quiz sin sannsynligiøring i en tabbel
2. lage dok krav som er aktiv hvis det kan linkes til en sannsynlgiøring
3. dok krav lever på utsiden av sannsynligiøring

 */
data class Dokumentkrav(
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
