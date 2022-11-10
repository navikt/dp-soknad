package no.nav.dagpenger.soknad.livssyklus.påbegynt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SøkerOppgaveMeldingTest {
    @Test
    fun `har sannynligjøringer`() {
        val json = this::class.java.getResource("/test-data/søker-oppgave.json").readText()
        val søkerOppgave = SøkerOppgaveMelding(json)
        val sannsynliggjøringer = søkerOppgave.sannsynliggjøringer()
        assertEquals(1, sannsynliggjøringer.size)

        with(sannsynliggjøringer.first()) {
            assertEquals("22.1", this.id)
            assertEquals(1, this.sannsynliggjør().size)
            with(this.sannsynliggjør().first()) {
                assertEquals("6.1", this.id)
            }
        }
    }
}
