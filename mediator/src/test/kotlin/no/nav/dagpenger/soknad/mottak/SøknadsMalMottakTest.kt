package no.nav.dagpenger.soknad.mottak

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.soknad.db.SøknadMal
import no.nav.dagpenger.soknad.db.SøknadMalRepository
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SøknadsMalMottakTest {

    private val søknadMalRepositoryMock = mockk<SøknadMalRepository>(relaxed = true)

    private val testRapid = TestRapid().also { rapidsConnection ->
        SøknadsMalMottak(rapidsConnection, søknadMalRepositoryMock)
    }

    @Test
    fun `Motta søknadsmal fra quiz og lagre den i databasen`() {
        val søknadMalSlot = slot<SøknadMal>()
        every { søknadMalRepositoryMock.lagre(capture(søknadMalSlot)) } just Runs
        testRapid.sendTestMessage(testSøknadMalMelding())
        verify(exactly = 1) {
            søknadMalRepositoryMock.lagre(
                any()
            )
        }

        assertTrue(søknadMalSlot.isCaptured)
        val mal = søknadMalSlot.captured.mal
        assertEquals(0, mal["seksjoner"].size())
        assertEquals("test", mal["versjon_navn"].asText())
        assertEquals(0, mal["versjon_id"].asInt())
    }
}

//language=JSON
fun testSøknadMalMelding() = """
    {
      "@event_name": "Søknadsmal",
      "versjon_id": 0,
      "versjon_navn": "test",
      "seksjoner": []
    }

""".trimIndent()
