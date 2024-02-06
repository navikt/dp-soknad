package no.nav.dagpenger.soknad.arbeidsforhold

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AaregClientTest {
    private val testTokenProvider: (token: String) -> String = { _ -> "testToken" }
    private val baseUrl = "http://baseUrl"
    private val subjectToken = "gylidg_token"
    private val fnr = "12345678903"

    private fun aaregClient(responseBody: String, statusCode: Int) = AaregClient(
        aaregUrl = baseUrl,
        tokenProvider = testTokenProvider,
        engine = createMockedClient(statusCode, responseBody),
    )

    @Test
    fun `hentArbeidsforhold svarer med tom liste dersom aareg responderer med 200 OK men ingen data `() {
        runBlocking {
            val client = aaregClient("[]", 200)
            val arbeidsforhold = client.hentArbeidsforhold(fnr, subjectToken)

            arbeidsforhold shouldBe emptyList()
            arbeidsforhold.toString() shouldBe "[]"
        }
    }

    @Test
    fun `hentArbeidsforhold svarer med liste over arbeidsforhold når aareg svarer med 200 og data`() {
        runBlocking {
            val client = aaregClient(mockAaregResponse, 200)

            val arbeidsforhold = client.hentArbeidsforhold(fnr, subjectToken)

            with(arbeidsforhold) {
                size shouldBe 2

                with(get(0)) {
                    id shouldBe "H911050676R16054L0001"
                    organisasjonsnummer shouldBe "910825518"
                    startdato shouldBe LocalDate.of(2014, 1, 1)
                    sluttdato shouldBe LocalDate.of(2015, 1, 1)
                    stillingsprosent shouldBe 100.0
                }

                with(get(1)) {
                    id shouldBe "V911050676R16054L0001"
                    organisasjonsnummer shouldBe "910825577"
                    startdato shouldBe LocalDate.of(2016, 1, 1)
                    sluttdato shouldBe null
                    stillingsprosent shouldBe 80.5
                }
            }
        }
    }

    @Test
    fun `hentArbeidsforhold håndterer mulige feilkoder fra aareg ved å returnere en tom liste`() {
        runBlocking {
            val feilkoderFraAareg = listOf(400, 401, 403, 404, 500)

            feilkoderFraAareg.forEach { feilkode ->
                val client = aaregClient("[]", feilkode)
                val arbeidsforhold = client.hentArbeidsforhold(fnr, subjectToken)
                arbeidsforhold shouldBe emptyList()
            }
        }
    }

    val mockAaregResponse =
        // language=JSON
        """
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
                },
                "ansettelsesdetaljer": [
                  {
                    "avtaltStillingsprosent": 100.0
                  }
                ]
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
                },
                "ansettelsesdetaljer": [
                  {
                    "avtaltStillingsprosent": 70.0,
                    "sisteStillingsprosentendring": "2022-01-01"
                  },
                  {
                    "avtaltStillingsprosent": 80.5,
                    "sisteStillingsprosentendring": "2024-01-01"
                  },
                  {
                    "avtaltStillingsprosent": 90.0,
                    "sisteStillingsprosentendring": "2023-01-01"
                  }
                ]
              }
            ]
        """.trimIndent()
}
