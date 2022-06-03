package no.nav.dagpenger.søknad.personalia

interface KontonummerInformasjon {
    val kontonummer: String?
    val banknavn: String?
    val bankLandkode: String?
}

data class Kontonummer(
    override val kontonummer: String? = null,
    override val banknavn: String? = null,
    override val bankLandkode: String? = null
) : KontonummerInformasjon
