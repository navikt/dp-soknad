package no.nav.dagpenger.soknad

interface SøknadData {
    fun erFerdig(): Boolean
    fun toJson(): String
}
