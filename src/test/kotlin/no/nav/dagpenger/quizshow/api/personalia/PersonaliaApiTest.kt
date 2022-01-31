package no.nav.dagpenger.quizshow.api.personalia

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.TestApplication
import no.nav.dagpenger.quizshow.api.TestApplication.autentisert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PersonaliaApiTest {

    @Test
    fun `Skal avvise uautentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            handleRequest(HttpMethod.Get, "${Configuration.basePath}/personalia") { }.apply {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `Hent personalia for autentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            autentisert(
                "${Configuration.basePath}/personalia",
                httpMethod = HttpMethod.Get,
            ).apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
            }
        }
    }
}
