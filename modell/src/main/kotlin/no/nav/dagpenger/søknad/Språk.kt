package no.nav.dagpenger.søknad

import java.util.Locale

data class Språk(
    val verdi: Locale
) {
    constructor(verdi: String) : this(Locale.forLanguageTag(verdi))
}
