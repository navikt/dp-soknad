package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class SvarTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Skal kunne opprette boolean type svar `() {
        val svar = Svar("boolean", true)
        assertDoesNotThrow {
            assertEquals(BooleanNode.TRUE, svar.validerOgKonverter())
        }
        assertThrows<IllegalArgumentException> { Svar("boolean", DecimalNode.valueOf(BigDecimal(2.0))).validerOgKonverter() }
    }

    @Test
    fun `Skal kunne opprette flervalg type svar`() {
        val svar = Svar("flervalg", listOf("valg1", "valg2"))
        assertDoesNotThrow {
            val forventet = objectMapper.createArrayNode()
            forventet.add("valg1")
            forventet.add("valg2")
            assertEquals(forventet, svar.validerOgKonverter())
        }
        assertThrows<IllegalArgumentException> { Svar("flervalg", emptyList<String>()).validerOgKonverter() }
    }

    @Test
    fun `Skal kunne opprette envalg type svar`() {
        val svar = Svar("envalg", "valg1")
        assertDoesNotThrow {
            assertEquals(TextNode.valueOf("valg1"), svar.validerOgKonverter())
        }
        assertThrows<IllegalArgumentException> { Svar("envalg", emptyList<String>()).validerOgKonverter() }
    }

    @Test
    fun `Skal kunne opprette localdate type svar`() {
        val nå = LocalDate.now()
        val svar = Svar("localdate", nå)
        assertDoesNotThrow {
            assertEquals(TextNode.valueOf(nå.toString()), svar.validerOgKonverter())
        }
        assertThrows<IllegalArgumentException> { Svar("localdate", "").validerOgKonverter() }
    }

    @Test
    fun `Typen Valg er gyldig dersom det er minst ett svaralternativ`() {
        val valgtSvar = listOf("valg1")
        val svarMedEttValg = Svar("envalg", valgtSvar)
        assertDoesNotThrow { svarMedEttValg.validerOgKonverter() }

        val valgtFlervalgsvar = objectMapper.createArrayNode()
        valgtFlervalgsvar.add("valg1")
        valgtFlervalgsvar.add("valg2")
        val svarMedFlereValg = Svar("envalg", valgtFlervalgsvar)
        assertDoesNotThrow { svarMedFlereValg.validerOgKonverter() }
    }
}
