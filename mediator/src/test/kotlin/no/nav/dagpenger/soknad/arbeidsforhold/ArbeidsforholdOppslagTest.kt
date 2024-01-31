package no.nav.dagpenger.soknad.arbeidsforhold

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val AAREG_API_HOST = "aaregApiHost"
private const val EREG_API_HOST = "eregApiHost"
class ArbeidsforholdOppslagTest {
    private val subjectToken = "gylidg_token"

    @BeforeEach
    fun setup() {
        System.setProperty("AAREG_API_HOST", AAREG_API_HOST)
        System.setProperty("EREG_API_HOST", EREG_API_HOST)
    }

    @Test
    fun `hentArbeidsforhold() should return empty list`() {
        runBlocking {
            val arbeidsforholdOppslag = ArbeidsforholdOppslag(tokenProvider = { subjectToken }, httpClient = createMockClient())

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold("fnr", subjectToken)

            with(arbeidsforhold) {
                size shouldBe 3
            }
        }
    }
}

fun createMockClient(): HttpClient {
    return HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                println("PATH: $request.url")

                when (request.url.host) {
                    AAREG_API_HOST -> respond(mockArbeidsforholdJson, HttpStatusCode.OK)
                    EREG_API_HOST -> respond(mockEREGResponse, HttpStatusCode.OK)
                    else -> respond(mockEREGResponse, HttpStatusCode.NotFound)
                }
            }
        }
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

private val mockEREGResponse = """{"navn": {"navnelinje1": "ABC AS"}}""".trimIndent()
