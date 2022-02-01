package no.nav.dagpenger.quizshow.api.personalia

import io.ktor.client.features.ClientRequestException
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.TestApplication
import no.nav.dagpenger.quizshow.api.TestApplication.autentisert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class KontonummerApiTest {

    @Test
    fun `Skal avvise uautentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            handleRequest(HttpMethod.Get, "${Configuration.basePath}/personalia/kontonummer") { }.apply {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `Hent kontonummer for autentiserte kall`() {
        val mockKontonummerOppslag = mockk<KontonummerOppslag>().also {
            coEvery { it.hentKontonummer(TestApplication.defaultDummyFodselsnummer) } returns Kontonummer("12234241211", "TUR", "SWE")
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(kontonummerOppslag = mockKontonummerOppslag)
        ) {
            autentisert(
                "${Configuration.basePath}/personalia/kontonummer",
                httpMethod = HttpMethod.Get,
            ).apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals(
                    ContentType.Application.Json.contentType,
                    this.response.contentType().contentType
                )
                assertEquals("""{"kontonummer":"12234241211","banknavn":"TUR","landkode":"SWE"}""", this.response.content!!)
                coVerify(exactly = 1) { mockKontonummerOppslag.hentKontonummer(TestApplication.defaultDummyFodselsnummer) }
            }
        }
    }

    @Test
    fun `Propagerer feil`() {

        val mockResponse = mockk<io.ktor.client.statement.HttpResponse>(relaxed = true).also {
            every { it.status } returns HttpStatusCode.NotFound
        }
        val mockKontonummerOppslag = mockk<KontonummerOppslag>().also {
            coEvery { it.hentKontonummer(TestApplication.defaultDummyFodselsnummer) } throws ClientRequestException(
                mockResponse,
                "FEil"
            )
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(kontonummerOppslag = mockKontonummerOppslag)
        ) {
            autentisert(
                "${Configuration.basePath}/personalia/kontonummer",
                httpMethod = HttpMethod.Get,
            ).apply {
                assertEquals(HttpStatusCode.NotFound, this.response.status())
                assertEquals(
                    ContentType.Application.Json.contentType,
                    this.response.contentType().contentType
                )
                assertEquals("""{"type":"urn:oppslag:kontonummer","title":"Feil ved uthenting av kontonummer","status":404,"detail":"Client request(Url(child^3 of #1#5#6#7)) invalid: 404 Not Found. Text: \"FEil\"","instance":"/arbeid/dagpenger/soknadapi/personalia/kontonummer"}""", this.response.content!!)
                coVerify(exactly = 1) { mockKontonummerOppslag.hentKontonummer(TestApplication.defaultDummyFodselsnummer) }
            }
        }
    }
}
