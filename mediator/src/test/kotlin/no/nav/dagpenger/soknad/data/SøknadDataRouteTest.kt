package no.nav.dagpenger.soknad.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.Configuration
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import no.nav.dagpenger.soknad.TestApplication.testTokenXToken
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadDataRouteTest {

    @Test
    fun `Skal avvise uautentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication() {
            Assertions.assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("${Configuration.basePath}/soknad/id/data").status,
            )

            Assertions.assertEquals(
                HttpStatusCode.Unauthorized,
                autentisert(
                    "${Configuration.basePath}/soknad/id/data",
                    token = testTokenXToken,
                ).status,
            )
        }
    }

    @Test
    fun `Skal hente søknad fakta`() {
        val testSøknadUuid = UUID.randomUUID()
        // language=JSON
        val frontendformat = """{"id":"blabla"}"""
        val søkerOppgave = mockk<SøkerOppgave>().also {
            every { it.toJson() } returns frontendformat
        }
        val mockSøknadMediator = mockk<SøknadMediator>().also { søknadMediator ->
            every { søknadMediator.hentSøkerOppgave(testSøknadUuid) } returns søkerOppgave
        }
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockSøknadMediator,
            ),
        ) {
            autentisert(
                endepunkt = "${Configuration.basePath}/soknad/$testSøknadUuid/data",
                token = TestApplication.azureAdToken,
            ).apply {
                Assertions.assertEquals(HttpStatusCode.OK, this.status)
                Assertions.assertEquals("application/json", this.headers["Content-Type"])
                Assertions.assertEquals(frontendformat, this.bodyAsText())
            }
        }
    }
}
