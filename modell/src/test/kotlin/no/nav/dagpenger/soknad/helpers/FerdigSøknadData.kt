package no.nav.dagpenger.soknad.helpers

import no.nav.dagpenger.soknad.SøknadData

val FerdigSøknadData: Lazy<SøknadData> = lazy {
    object : SøknadData {
        override fun erFerdig() = true
        override fun toJson(): String = ""
    }
}
