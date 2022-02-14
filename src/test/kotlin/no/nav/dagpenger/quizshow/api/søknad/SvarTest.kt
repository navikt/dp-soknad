package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class SvarTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Skal kunne opprette gyldig Svar objekt`() {
        val gyldigSvar = Svar("boolean", BooleanNode.TRUE)
        assertDoesNotThrow { gyldigSvar.valider() }
    }

    @Test
    fun `Skal kaste feil hvis svaret inneholder ugyldig verdi`() {
        val ugyldigSvar = Svar("flervalg", objectMapper.createArrayNode())
        assertThrows<IllegalArgumentException> { ugyldigSvar.valider() }
    }

    @Test
    fun `Typen Valg er gyldig dersom det er minst ett svaralternativ`() {
        val valgtSvar = objectMapper.createArrayNode()
        valgtSvar.add("valg1")
        val svarMedEttValg = Svar("envalg", valgtSvar)
        assertDoesNotThrow { svarMedEttValg.valider() }

        val valgtFlervalgsvar = objectMapper.createArrayNode()
        valgtFlervalgsvar.add("valg1")
        valgtFlervalgsvar.add("valg2")
        val svarMedFlereValg = Svar("envalg", valgtFlervalgsvar)
        assertDoesNotThrow { svarMedFlereValg.valider() }
    }
}
