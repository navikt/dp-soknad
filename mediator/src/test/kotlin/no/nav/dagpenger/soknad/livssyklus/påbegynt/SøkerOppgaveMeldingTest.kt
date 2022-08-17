package no.nav.dagpenger.soknad.livssyklus.påbegynt

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SøkerOppgaveMeldingTest {

    @Test
    fun `har sannynligjøringer`() {
        val json = this::class.java.getResource("/test-data/søker-oppgave.json").readText()
        val søkerOppgave = SøkerOppgaveMelding(
            JsonMessage(json, MessageProblems(json)).also {
                it.requireKey("seksjoner")
            }
        )

        val sannsynliggjøringer = søkerOppgave.sannsynliggjøringer()
        assertEquals(1, sannsynliggjøringer.size)

        with(sannsynliggjøringer.first()) {
            assertEquals("22.1", this.id)
        }
    }
}
