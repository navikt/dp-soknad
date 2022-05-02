package no.nav.dagpenger.soknad.mottak

import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.soknad.db.SøknadMalRepository
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test

class SøknadsMalMottakTest {

    private val søknadMalRepositoryMock = mockk<SøknadMalRepository>(relaxed = true)

    private val testRapid = TestRapid().also { rapidsConnection ->
        SøknadsMalMottak(rapidsConnection, søknadMalRepositoryMock)
    }

    @Test
    fun `Motta søknadsmal fra quiz og lagre den i databasen`() {
        testRapid.sendTestMessage(testMessage())
        verify(exactly = 1) { søknadMalRepositoryMock.lagre("test", 0, any()) }
    }
}

//language=JSON
fun testMessage() = """
    {
      "@event_name": "Søknadsmal",
      "versjon_id": 0,
      "versjon_navn": "test",
      "seksjoner": []
    }

""".trimIndent()
