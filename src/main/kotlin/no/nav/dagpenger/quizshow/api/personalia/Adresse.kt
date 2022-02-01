package no.nav.dagpenger.quizshow.api.personalia

data class Adresse(
    val adresselinje1: String = "",
    val adresselinje2: String = "",
    val adresselinje3: String = "",
    val byEllerStedsnavn: String = "",
    val landkode: String = "",
    val land: String = "",
    val postkode: String = "",
)
