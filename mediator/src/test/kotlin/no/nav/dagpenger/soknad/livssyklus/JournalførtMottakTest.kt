package no.nav.dagpenger.soknad.livssyklus

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.innsending.InnsendingMediator
import no.nav.dagpenger.innsending.tjenester.JournalførtMottak
import no.nav.dagpenger.soknad.hendelse.innsending.JournalførtHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

internal class JournalførtMottakTest {

    @ParameterizedTest
    @ValueSource(strings = ["NySøknad", "Ettersending", "Gjenopptak", "Generell"])
    fun `Skal behandle innsending_ferdigstilt event for type NySøknad, Gjenopptak, Ettersending og Generell`(type: String) {
        val søknadId = UUID.randomUUID()
        val journalpostId = "jp1"
        val ident = "ident1"
        TestRapid().let { testRapid ->
            val slot = slot<JournalførtHendelse>()
            JournalførtMottak(
                testRapid,
                mockk<InnsendingMediator>().also {
                    every { it.behandleJournalførtHendelse(capture(slot)) } just Runs
                },
            )

            testRapid.sendTestMessage(
                `innsending ferdigstilt hendelse`(
                    søknadId = søknadId,
                    journalpostId = journalpostId,
                    type = type,
                    ident = ident,
                ),
            )
            assertTrue(slot.isCaptured)
            with(slot.captured) {
                assertEquals(this.journalpostId(), journalpostId)
                assertEquals(this.ident(), ident)
            }
        }
    }

    private fun `innsending ferdigstilt hendelse`(
        søknadId: UUID,
        journalpostId: String,
        type: String,
        ident: String,
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

@Language("JSON")
val lol = """
{
  "@id": "88edc131-ca7a-4698-b460-4bc52eeebf4b",
  "@opprettet": "2023-01-05T19:30:49.892895037",
  "journalpostId": "598734831",
  "datoRegistrert": "2023-01-05T19:30:43",
  "skjemaKode": "GENERELL_INNSENDING",
  "tittel": "Generell innsending",
  "type": "Generell",
  "fødselsnummer": "27028327825",
  "aktørId": "1000096570597",
  "søknadsData": {},
  "@event_name": "innsending_ferdigstilt",
  "system_read_count": 0,
  "system_participating_services": [
    {
      "id": "88edc131-ca7a-4698-b460-4bc52eeebf4b",
      "time": "2023-01-05T19:30:49.893140946",
      "service": "dp-mottak",
      "instance": "dp-mottak-5f57874678-nlblk",
      "image": "ghcr.io/navikt/dp-mottak/dp-mottak:cfa50014c9c559ee1e211934ab040db054818642"
    }
  ]
}
""".trimIndent()
