package no.nav.dagpenger.soknad.arbeidsforhold

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AaregClientTest {
    private val testTokenProvider: (token: String) -> String = { _ -> "testToken" }
    private val baseUrl = "http://baseUrl"
    val subjectToken = "gylidg_token"

    @Test
    fun `aareg svarer med 200 og en tom liste av arbeidsforhold`() {
        val mockEngine =
            MockEngine {
                respond(
                    content = ByteReadChannel("[]"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

        val aaregClient =
            AaregClient(
                aaregUrl = baseUrl,
                tokenProvider = testTokenProvider,
                engine = mockEngine,
            )

        val arbeidsforhold = aaregClient.hentArbeidsforhold(fnr = "12345678903", subjectToken = subjectToken)
        arbeidsforhold shouldBe emptyList()
        arbeidsforhold.toString() shouldBe "[]"
    }

    @Test
    fun `aareg svarer med 200 og liste med to arbeidsforhold`() {
        val mockEngine =
            MockEngine {
                respond(
                    content = ByteReadChannel(mockArbeidsforhold()),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

        val aaregClient =
            AaregClient(
                aaregUrl = baseUrl,
                tokenProvider = testTokenProvider,
                engine = mockEngine,
            )

        val arbeidsforhold = aaregClient.hentArbeidsforhold(fnr = "12345678903", subjectToken = subjectToken)
        arbeidsforhold.size shouldBe 2

        with(arbeidsforhold[0]) {
            id shouldBe "H911050676R16054L0001"
            organisasjonsnummer shouldBe "910825518"
            startdato shouldBe LocalDate.of(2014, 1, 1)
            sluttdato shouldBe LocalDate.of(2015, 1, 1)
        }

        with(arbeidsforhold[1]) {
            id shouldBe "V911050676R16054L0001"
            organisasjonsnummer shouldBe "910825577"
            startdato shouldBe LocalDate.of(2016, 1, 1)
            sluttdato shouldBe null
        }
    }

    fun mockArbeidsforhold(): String {
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
