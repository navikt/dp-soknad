package no.nav.dagpenger.soknad.arbeidsforhold

import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.junit.jupiter.api.Test

class EregClientTest {

    @Test
    fun `henter organisasjonsnavn`() {
        val client = createMockedEregClient(200, """{"navn": {"navnelinje1": "ABC AS"}}""")

        client.hentOganisasjonsnavn("123456789") shouldBe "ABC AS"
    }

    @Test
    fun `kan ikke hente organisasjonsnavn`() {
        val client = createMockedEregClient(404, "")

        client.hentOganisasjonsnavn("123456789") shouldBe null

    }

    private fun createMockedEregClient(statusCode: Int, responseBody: String): EregClient {
        val mockEngine =
            MockEngine {
                respond(
                    content = responseBody,
                    status = HttpStatusCode.fromValue(statusCode),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

        return EregClient(eregUrl = "http://example.com", engine = mockEngine)
    }
}


