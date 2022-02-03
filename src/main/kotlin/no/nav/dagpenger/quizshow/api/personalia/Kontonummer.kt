package no.nav.dagpenger.quizshow.api.personalia

interface KontonummerInformasjon {
    val kontonummer: String
    val banknavn: String?
    val landkode: String?
}

data class Kontonummer(
    override val kontonummer: String,
    override val banknavn: String?,
    override val landkode: String?
) : KontonummerInformasjon
