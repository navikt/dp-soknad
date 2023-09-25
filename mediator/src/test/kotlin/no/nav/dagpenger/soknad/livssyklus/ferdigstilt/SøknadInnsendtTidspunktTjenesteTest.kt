package no.nav.dagpenger.soknad.livssyklus.ferdigstilt

import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadData
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.Before
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.lang.RuntimeException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class SøknadInnsendtTidspunktTjenesteTest {
    private val testRapid = TestRapid()
    private val okSoknadId = UUID.fromString("73121901-a0c2-4e30-a804-815cc706b9fd")
    private val finnesIkkeSoknadId = UUID.fromString("e209d004-c66d-415a-8574-1fb7f064ec7a")
    private val kasterFeilSoknadId = UUID.fromString("773727f6-e969-4281-85ef-cd8ad8e76cfd")
    private val innsendtTidspunkt = LocalDate.of(2022, 5, 5).atStartOfDay(ZoneId.of("Europe/Oslo"))
    private val now = ZonedDateTime.now()

    init {
        SøknadInnsendtTidspunktTjeneste(
            rapidsConnection = testRapid,

            mediator = mockk<SøknadMediator>().also {
                every { it.hent(okSoknadId) } returns Søknad.rehydrer(
                    søknadId = okSoknadId,
                    ident = "",
                    opprettet = now,
                    innsendt = innsendtTidspunkt,
                    språk = Språk(verdi = "NN"),
                    dokumentkrav = Dokumentkrav(),
                    sistEndretAvBruker = now,
                    tilstandsType = Søknad.Tilstand.Type.Innsendt,
                    aktivitetslogg = Aktivitetslogg(forelder = null),
                    prosessversjon = null,
                    data = lazy<SøknadData> {
                        object : SøknadData {
                            override fun erFerdig() = true
                        }
                    },
                )
                every { it.hent(finnesIkkeSoknadId) } returns null
                every { it.hent(kasterFeilSoknadId) } throws RuntimeException("test")
            },
        )
    }

    @Before
    fun reset() {
        testRapid.reset()
    }

    @Test
    fun `skal svare på behov dp-quiz-mediator`() {
        testRapid.sendTestMessage(quizBehovMelding(okSoknadId))
        with(testRapid.inspektør) {
            Assertions.assertEquals(1, size)
            Assertions.assertEquals("2022-05-05", field(0, "@løsning")["Søknadstidspunkt"].asText())
        }
    }

    @Test
    fun `skal svare på behov fra dp-rapportering`() {
        testRapid.sendTestMessage(rapporteringBehovMelding(okSoknadId))
        with(testRapid.inspektør) {
            Assertions.assertEquals(1, size)
            Assertions.assertEquals("2022-05-05", field(0, "@løsning")["Søknadstidspunkt"].asText())
        }
    }

    @Test
    fun `skal svelge feil `() {
        testRapid.sendTestMessage(quizBehovMelding(finnesIkkeSoknadId))
        testRapid.sendTestMessage(quizBehovMelding(kasterFeilSoknadId))
        with(testRapid.inspektør) {
            Assertions.assertEquals(0, size)
        }
    }

    //language=JSON
    private fun quizBehovMelding(soknadId: UUID) =
        """
    {
      "@event_name": "faktum_svar",
      "@opprettet": "2020-11-18T11:04:32.867824",
      "@id": "930e2beb-d394-4024-b713-dbeb6ad3d4bf",
      "@behovId": "930e2beb-d394-4024-b713-dbeb6ad3d4bf",
      "identer":[{"id":"12345678910","type":"folkeregisterident","historisk":false}],
      "søknad_uuid": "41621ac0-f5ee-4cce-b1f5-88a79f25f1a5",
      "@behov": [ "Søknadstidspunkt" ],
      "InnsendtSøknadsId":{"lastOppTidsstempel":"2020-11-26T10:33:38.684844","urn":"urn:soknadid:$soknadId"}
    }
        """.trimIndent()

    //language=JSON
    private fun rapporteringBehovMelding(soknadId: UUID) =
        """
            {
              "@event_name": "behov",
              "@behovId": "05a2b4e3-def6-4973-9f67-52bc66e55b1a",
              "@behov": [
                "Søknadstidspunkt"
              ],
              "Søknadstidspunkt": {
                "søknad_uuid": "$soknadId"
              },
              "@id": "507434ad-4a46-4494-ac3c-c1459a2a6a2e",
              "@opprettet": "2023-06-29T15:10:13.351324642"
            }
        """.trimIndent()
}
