package no.nav.dagpenger.soknad.mottak

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.soknad.db.SøknadMal
import no.nav.dagpenger.soknad.db.SøknadMalRepository
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
        every { søknadMalRepositoryMock.lagre(capture(søknadMalSlot)) } returns 1
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
        assertEquals(111, mal["versjon_id"].asInt())

        testRapid.inspektør.also {
            assertEquals(1, it.size)
            assertEquals("ny_quiz_mal", it.message(0)["@event_name"].asText())
            assertEquals("test", it.message(0)["versjon_navn"].asText())
            assertEquals(111, it.message(0)["versjon_id"].asInt())
            assertNotNull(it.message(0)["@opprettet"])
        }
    }
}

//language=JSON
fun testSøknadMalMelding() = """
    {
      "@event_name": "Søknadsmal",
      "versjon_id": 111,
      "versjon_navn": "test",
      "seksjoner": []
    }

""".trimIndent()
