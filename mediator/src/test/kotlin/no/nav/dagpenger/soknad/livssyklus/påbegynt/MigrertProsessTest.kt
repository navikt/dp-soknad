package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.util.UUID

class MigrertProsessTest {
    private val søknadUUID = UUID.randomUUID()
    private val ident = "123"
    private val mediatorMock = mockk<SøknadMediator>()
    private val testRapid = TestRapid().also { rapidsConnection ->
        MigrertSøknadMottak(rapidsConnection, mediatorMock)
    }

    @Test
    fun `skal lese migrert melding`() {
        every {
            mediatorMock.behandle(any<MigrertProsessHendelse>(), any())
        } just Runs

        testRapid.sendTestMessage(melding)

        verify { mediatorMock.behandle(any<MigrertProsessHendelse>(), any()) }
    }

    //language=JSON
    private val melding = """
        {
        "@event_name": "behov",
        "@behov": ["MigrertProsess"],
        "søknad_uuid": "$søknadUUID",
        "ident": "$ident",
        "@løsning": {
           "MigrertProsess": 
              {
               "prosessnavn": "Dagpenger",
                "versjon": 1,
                "data": {}
               }
          }
        }
    """.trimIndent()
}
