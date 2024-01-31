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
            val arbeidsforholdOppslag = ArbeidsforholdOppslag(
                tokenProvider = { subjectToken },
                httpClient = createMockClient(
                    mockAaregResponse = mockAaregResponse,
                    mockEregResponse = mockEregResponse,
                ),
            )

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold("fnr", subjectToken)

            with(arbeidsforhold) {
                size shouldBe 2

                with(get(0)) {
                    id shouldBe "H911050676R16054L0001"
                    organisasjonsnavn shouldBe "ABC AS"
                }

                with(get(1)) {
                    id shouldBe "V911050676R16054L0001"
                    organisasjonsnavn shouldBe "ABC AS"
                }
            }
        }
    }
}

fun createMockClient(mockAaregResponse: String, mockEregResponse: String): HttpClient {
    return HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when (request.url.host) {
                    AAREG_API_HOST -> respond(mockAaregResponse, HttpStatusCode.OK)
                    EREG_API_HOST -> respond(mockEregResponse, HttpStatusCode.OK)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
        }
    }
}
