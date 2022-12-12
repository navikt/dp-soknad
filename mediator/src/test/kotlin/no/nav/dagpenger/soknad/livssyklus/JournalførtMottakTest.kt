package no.nav.dagpenger.soknad.livssyklus

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.Innsending.TilstandType.Journalført
import no.nav.dagpenger.soknad.hendelse.innsending.JournalførtHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.innsending.tjenester.JournalførtMottak
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.ZonedDateTime
import java.util.UUID

internal class JournalførtMottakTest {

    @ParameterizedTest
    @ValueSource(strings = ["NySøknad", "Ettersending", "Gjenopptak", "Generell"])
    fun `Skal behandle innsending_ferdigstilt event for type NySøknad, Gjenopptak, Ettersending og Generell`(type: String) {
        val søknadId = UUID.randomUUID()
        val journalpostId = "jp1"
        val ident = "ident1"
        val innsendingId = UUID.randomUUID()
        TestRapid().let { testRapid ->
            val slot = slot<JournalførtHendelse>()
            JournalførtMottak(
                testRapid,
                mockk<InnsendingMediator>().also {
                    every { it.behandle(capture(slot)) } just Runs
                    every { it.hentFor(søknadId) } returns listOf(
                        Innsending.rehydrer(
                            innsendingId = innsendingId,
                            type = Innsending.InnsendingType.NY_DIALOG,
                            ident = ident,
                            søknadId = søknadId,
                            innsendt = ZonedDateTime.now(),
                            journalpostId = "jp",
                            tilstandsType = Journalført,
                            hovedDokument = null,
                            dokumenter = listOf(),
                            metadata = null
                        )
                    )
                }
            )

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
                assertEquals(this.innsendingId(), innsendingId)
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
