package no.nav.dagpenger.soknad.livssyklus

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

internal class JournalførtMottakTest {

    @ParameterizedTest
    @ValueSource(strings = ["NySøknad", "Ettersending"])
    fun `Skal behandle innsending_ferdigstilt event for type NySøknad og Ettersending`(type: String) {
        TestRapid().let { testRapid ->
            val slot = slot<JournalførtHendelse>()
            JournalførtMottak(
                testRapid,
                mockk<SøknadMediator>().also {
                    every { it.behandle(capture(slot)) } just Runs
                }
            )
            val søknadId = UUID.randomUUID()
            val journalpostId = "jp1"
            val ident = "ident1"

            testRapid.sendTestMessage(
                `innsending ferdigstilt hendelse`(
                    søknadId = søknadId,
                    journalpostId = journalpostId,
                    type = type,
                    ident = ident
                )
            )
            assertTrue(slot.isCaptured)
            with(slot.captured) {
                assertEquals(this.journalpostId(), journalpostId)
                assertEquals(this.ident(), ident)
                assertEquals(this.søknadID(), søknadId)
            }
        }
    }

    private fun `innsending ferdigstilt hendelse`(
        søknadId: UUID,
        journalpostId: String,
        type: String,
        ident: String
    ): String {
        return """
{
  "journalpostId": "$journalpostId",
  "type": "$type",
  "fødselsnummer": "$ident",
  "søknadsData": {
    "søknad_uuid": "$søknadId"
  },
  "@event_name": "innsending_ferdigstilt"
} 
        """.trimIndent()
    }
}
