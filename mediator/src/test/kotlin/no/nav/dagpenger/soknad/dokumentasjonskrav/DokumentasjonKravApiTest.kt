package no.nav.dagpenger.soknad.dokumentasjonskrav

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class DokumentasjonKravApiTest {

    private val testSoknadId = "d172a832-4f52-4e1f-ab5f-8be8348d9280"

    @Test
    fun `Skal avvise uautentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication() {
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("${Configuration.basePath}/soknad/id/dokumentasjonkrav").status
            )
        }
    }

    @Test
    fun `skal vise dokumentasjons krav`() {
        TestApplication.withMockAuthServerAndTestApplication() {
            autentisert(
                httpMethod = Get,
                endepunkt = "${Configuration.basePath}/soknad/$testSoknadId/dokumentasjonkrav"
            ).let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())
                assertNotNull(response.bodyAsText().also { println(it) })
            }
        }
    }

    @Test
    fun `Skal kunne besvare`() {
        TestApplication.withMockAuthServerAndTestApplication() {
            client.put("${Configuration.basePath}/soknad/$testSoknadId/dokumentasjonkrav/451") {
                autentisert()
                header(HttpHeaders.ContentType, "application/json")
                setBody("""{"svar": "ja"}""")
            }.let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())
                assertNotNull(response.bodyAsText().also { println(it) })
            }
        }
    }
}
