package no.nav.dagpenger.soknad.arbeidsforhold

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AaregClientTest {
    private val testTokenProvider: (token: String) -> String = { _ -> "testToken" }
    private val eregClientMock = mockk<EregClient>()
    private val baseUrl = "http://baseUrl"
    private val subjectToken = "gylidg_token"

    private fun aaregClient(responseBody: String) = AaregClient(
        aaregUrl = baseUrl,
        eregClient = eregClientMock,
        tokenProvider = testTokenProvider,
        engine = createMockedClient(200, responseBody),
    )

    @Test
    fun `aareg svarer med 200 og en tom liste av arbeidsforhold`() {
        val client = aaregClient("[]")

        val arbeidsforhold = client.hentArbeidsforhold(fnr = "12345678903", subjectToken = subjectToken)

        arbeidsforhold shouldBe emptyList()
        arbeidsforhold.toString() shouldBe "[]"
    }

    @Test
    fun `aareg svarer med 200 og liste med to arbeidsforhold`() {
        coEvery { eregClientMock.hentOganisasjonsnavn(any()) } returns "ABC AS" andThen "DEF AS" andThen null
        val client = aaregClient(mockArbeidsforholdJson)

        val arbeidsforhold = client.hentArbeidsforhold(fnr = "12345678903", subjectToken = subjectToken)

        with(arbeidsforhold) {
            size shouldBe 2

            with(get(0)) {
                id shouldBe "H911050676R16054L0001"
                organisasjonsnavn shouldBe "ABC AS"
                startdato shouldBe LocalDate.of(2014, 1, 1)
                sluttdato shouldBe LocalDate.of(2015, 1, 1)
            }

            with(get(1)) {
                id shouldBe "V911050676R16054L0001"
                organisasjonsnavn shouldBe "DEF AS"
                startdato shouldBe LocalDate.of(2016, 1, 1)
                sluttdato shouldBe null
            }
        }

        coVerify { eregClientMock.hentOganisasjonsnavn("910825518") }
        coVerify { eregClientMock.hentOganisasjonsnavn("910825577") }
        coVerify { eregClientMock.hentOganisasjonsnavn(null) }
    }

    @Test
    fun `arbeidsforhold som ikke har gyldig organisasjonsnummer`() {
        coEvery { eregClientMock.hentOganisasjonsnavn(any()) } returns "ABC AS" andThen "DEF AS" andThen null
        val client = aaregClient(mockArbeidsforholdJson)

        val arbeidsforhold = client.hentArbeidsforhold(fnr = "12345678903", subjectToken = subjectToken)

        with(arbeidsforhold) {
            size shouldBe 2
            get(0).organisasjonsnavn shouldBe "ABC AS"
            get(1).organisasjonsnavn shouldBe "DEF AS"
        }

        coVerify { eregClientMock.hentOganisasjonsnavn("910825518") }
        coVerify { eregClientMock.hentOganisasjonsnavn("910825577") }
        coVerify { eregClientMock.hentOganisasjonsnavn(null) }
    }
}

private val mockArbeidsforholdJson = """
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
              }, 
              {
                "id": "V911050676R16054L0003",
                "arbeidssted": {
                  "type": "Person",
                  "identer": [
                    {
                      "type": "AKTØR_ID",
                      "ident": "12345678903"
                    }
                  ]
                },
                "ansettelsesperiode": {
                  "startdato": "2016-01-01"
                }
              }
              
            ]
""".trimIndent()
