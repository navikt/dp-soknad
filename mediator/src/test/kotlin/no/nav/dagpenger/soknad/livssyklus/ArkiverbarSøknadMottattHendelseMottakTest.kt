package no.nav.dagpenger.soknad.livssyklus

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.hendelse.innsending.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.innsending.tjenester.ArkiverbarSøknadMottattHendelseMottak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ArkiverbarSøknadMottattHendelseMottakTest {
    private val slot = slot<ArkiverbarSøknadMottattHendelse>()

    private val mediatorMock = mockk<InnsendingMediator>().also {
        every { it.behandle(capture(slot)) } just Runs
    }

    private val testRapid = TestRapid().also { rapidsConnection ->
        ArkiverbarSøknadMottattHendelseMottak(rapidsConnection, mediatorMock)
    }

    @Test
    fun `skal lytte på arkiverbar søknad opprettet melding og oversette til ArkiverbarSøknadMottattHendelse`() {
        testRapid.sendTestMessage(losning)
        verify(exactly = 1) { mediatorMock.behandle(any() as ArkiverbarSøknadMottattHendelse) }
        val dokumentvarianter: List<Innsending.Dokument.Dokumentvariant> = slot.captured.dokumentvarianter().sortedBy { it.urn }

        assertEquals(2, dokumentvarianter.size)

        dokumentvarianter.first().let { dokumentvariant ->
            assertEquals("brutto.pdf", dokumentvariant.filnavn)
            assertEquals("urn:vedlegg:soknadId/brutto.pdf", dokumentvariant.urn)
            assertEquals("FULLVERSJON", dokumentvariant.variant)
            assertEquals("PDF", dokumentvariant.type)
        }

        dokumentvarianter.last().let { dokumentvariant ->
            assertEquals("netto.pdf", dokumentvariant.filnavn)
            assertEquals("urn:vedlegg:soknadId/netto.pdf", dokumentvariant.urn)
            assertEquals("ARKIV", dokumentvariant.variant)
            assertEquals("PDF", dokumentvariant.type)
        }
    }

    //language=JSON
    private val losning = """
       {
  "@event_name": "behov",
  "@behov": [
    "ArkiverbarSøknad"
  ],
  "søknad_uuid": "f83b0db7-9555-4d1d-b5db-7ab8e3e9d1c8",
  "innsendingId": "f83b0db7-9555-4d1d-b5db-7ab8e3e9d1c8",
  "ident": "12345678910",
  "innsendtTidspunkt": "2022-05-20T12:04:20.000625+02:00[Europe/Oslo]",
  "@id": "0d23e605-0485-4335-aafc-27015e8fcc9e",
  "@opprettet": "2022-05-20T12:04:21.955036",
  "system_read_count": 0,
  "system_participating_services": [
    {
      "id": "d29c1547-4832-4cf2-9f14-117dd2cc9110",
      "time": "2022-05-20T12:04:20.043727"
    },
    {
      "id": "0d23e605-0485-4335-aafc-27015e8fcc9e",
      "time": "2022-05-20T12:04:21.955036"
    }
  ],
  "@final": true,
  "@løsning": {
    "ArkiverbarSøknad": [
                  {
                    "metainfo": {
                      "innhold": "netto.pdf",
                      "filtype": "PDF", 
                      "variant": "NETTO"
                    },
                    "urn": "urn:vedlegg:soknadId/netto.pdf"
                  },
                  {
                    "metainfo": {
                      "innhold": "brutto.pdf",
                      "filtype": "PDF",
                      "variant": "BRUTTO"
                    },
                    "urn": "urn:vedlegg:soknadId/brutto.pdf"
                  }
                ]
  },
  "@forårsaket_av": {
    "id": "d29c1547-4832-4cf2-9f14-117dd2cc9110",
    "opprettet": "2022-05-20T12:04:20.043727",
    "event_name": "behov",
    "behov": [
      "ArkiverbarSøknad"
    ]
  }
} 
    """.trimIndent()
}
