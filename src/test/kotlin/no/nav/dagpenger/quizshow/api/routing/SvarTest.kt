package no.nav.dagpenger.quizshow.api.routing

import BadRequestException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class SvarTest {

    @Test
    fun `Skal kunne opprette gyldig Svar objekt`() {
        val svar = Svar("boolean", true)
        assertDoesNotThrow { svar.valider() }
    }

    @Test
    fun `Skal kaste feil hvis svaret inneholder ugyldig verdi`() {
        val svar = Svar("valg", emptyList<String>())
        assertThrows<BadRequestException> { svar.valider() }
    }

    @Test
    fun `Typen Valg er gyldig dersom det er minst ett svaralternativ`() {

        val svar = Svar("valg", (listOf("valg1")))
        assertDoesNotThrow { svar.valider() }

        val svarMedFlereAlternativer = Svar("valg", (listOf("valg1", "valg2")))
        assertDoesNotThrow { svarMedFlereAlternativer.valider() }
    }
}
