package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.soknad.SøknadMediator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class MigrertSøknadMottakTest {
    private val søknadUUID = UUID.randomUUID()
    private val ident = "123"
    private val mediatorMock = mockk<SøknadMediator>()
    private val testRapid =
        TestRapid().also { rapidsConnection ->
            MigrertSøknadMottak(rapidsConnection, mediatorMock)
        }

    @Test
    fun `skal lese migrert melding`() {
        every {
            mediatorMock.behandle(any(), any())
        } just Runs

        testRapid.sendTestMessage(melding)
        val søkerOppgave = slot<SøkerOppgave>()
        verify { mediatorMock.behandle(any(), capture(søkerOppgave)) }

        with(søkerOppgave.captured) {
            assertEquals(ident, this.eier())
            assertEquals(søknadUUID, this.søknadUUID())
        }
    }

    //language=JSON
    private val melding =
        """
        {
        "@event_name": "behov",
        "@behov": ["MigrerProsess"],
        "søknad_uuid": "$søknadUUID",
        "ident": "$ident",
        "@løsning": {
           "MigrerProsess": 
              {
               "prosessnavn": "Dagpenger",
                "versjon": 1,
                "data": "{\"fødselsnummer\":\"$ident\",\"@event_name\":\"søker_oppgave\",\"versjon_id\":246,\"versjon_navn\":\"Dagpenger\",\"@opprettet\":\"2022-11-10T21:01:04.473809682\",\"@id\":\"68c04444-3788-4b11-b937-12809a9e3697\",\"søknad_uuid\":\"$søknadUUID\",\"ferdig\":false,\"seksjoner\":[],\"antallSeksjoner\":10}"
               }
          }
        }
        """.trimIndent()
}
