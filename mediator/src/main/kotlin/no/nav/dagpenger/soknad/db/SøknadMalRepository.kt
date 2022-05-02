package no.nav.dagpenger.soknad.db

import com.fasterxml.jackson.databind.JsonNode

interface SøknadMalRepository {
    fun lagre(søknadMal: SøknadMal)
    fun hentNyesteMal(prosessnavn: String): SøknadMal
}

data class SøknadMal(val prosessnavn: String, val prosessversjon: Int, val mal: JsonNode)

class IngenMalFunnetException(override val message: String? = "Fant ingen søknadmal") : RuntimeException(message)
