package no.nav.dagpenger.soknad.arbeidsforhold

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AaregClientTest {
    private val testTokenProvider: (token: String) -> String = { _ -> "testToken" }
    private val eregClientMock = mockk<EregClient>()
    private val baseUrl = "http://baseUrl"
    val subjectToken = "gylidg_token"

    @Test
    fun `aareg svarer med 200 og en tom liste av arbeidsforhold`() {
        val aaregClient =
            AaregClient(
                aaregUrl = baseUrl,
                eregClient = eregClientMock,
                tokenProvider = testTokenProvider,
                engine = createMockedClient(200, "[]"),
            )

        val arbeidsforhold = aaregClient.hentArbeidsforhold(fnr = "12345678903", subjectToken = subjectToken)
        arbeidsforhold shouldBe emptyList()
        arbeidsforhold.toString() shouldBe "[]"
    }

    @Test
    fun `aareg svarer med 200 og liste med to arbeidsforhold`() {
        coEvery { eregClientMock.hentOganisasjonsnavn(any()) } returnsMany listOf("ABC AS", "DEF AS")

        val aaregClient =
            AaregClient(
                aaregUrl = baseUrl,
                eregClient = eregClientMock,
                tokenProvider = testTokenProvider,
                engine = createMockedClient(200, mockArbeidsforhold()),
            )

        val arbeidsforhold = aaregClient.hentArbeidsforhold(fnr = "12345678903", subjectToken = subjectToken)
        arbeidsforhold.size shouldBe 2

        with(arbeidsforhold[0]) {
            id shouldBe "H911050676R16054L0001"
            organisasjonsnavn shouldBe "ABC AS"
            startdato shouldBe LocalDate.of(2014, 1, 1)
            sluttdato shouldBe LocalDate.of(2015, 1, 1)
        }

        with(arbeidsforhold[1]) {
            id shouldBe "V911050676R16054L0001"
            organisasjonsnavn shouldBe "DEF AS"
            startdato shouldBe LocalDate.of(2016, 1, 1)
            sluttdato shouldBe null
        }
    }

    private fun mockArbeidsforhold(): String {
        //language=JSON
        return """
            [
              {
                "id": "H911050676R16054L0001",
                "arbeidssted": {
                  "type": "Underenhet",
                  "identer": [
                    {
                      "type": "ORGANISASJONSNUMMER",
                      "ident": "910825518"
                    }
                  ]
                },
                "ansettelsesperiode": {
                  "startdato": "2014-01-01",
                  "sluttdato": "2015-01-01"
                }
              },
              {
                "id": "V911050676R16054L0001",
                "arbeidssted": {
                  "type": "Underenhet",
                  "identer": [
                    {
                      "type": "ORGANISASJONSNUMMER",
                      "ident": "910825577"
                    }
                  ]
                },
                "ansettelsesperiode": {
                  "startdato": "2016-01-01"
                }
              }
            ]
        """.trimIndent()
    }
}