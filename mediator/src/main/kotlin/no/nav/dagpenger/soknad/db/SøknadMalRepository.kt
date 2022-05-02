package no.nav.dagpenger.soknad.db

import com.fasterxml.jackson.databind.JsonNode

interface SøknadMalRepository {
    fun lagre(prosessnavn: String, prosessversjon: Int, søknadsMal: JsonNode)
}
