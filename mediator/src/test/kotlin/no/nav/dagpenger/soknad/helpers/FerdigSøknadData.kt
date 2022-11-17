import no.nav.dagpenger.soknad.SøknadData

val FerdigSøknadData: Lazy<SøknadData> = lazy {
    object : SøknadData {
        override fun erFerdig() = true
    }
}
