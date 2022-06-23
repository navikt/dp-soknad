package no.nav.dagpenger.søknad.livssyklus.påbegynt

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.søknad.Configuration
import no.nav.dagpenger.søknad.SøknadMediator
import no.nav.dagpenger.søknad.TestApplication
import no.nav.dagpenger.søknad.TestApplication.autentisert
import no.nav.dagpenger.søknad.livssyklus.PåbegyntSøknad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class PåbegyntSøknadApiTest {

    @Test
    fun `Skal hente påbegynt søknad`() {
        val expectedSoknad = PåbegyntSøknad(
            UUID.fromString("258b2f1b-bdda-4bed-974c-c4ddb206e4f4"),
            LocalDate.of(2021, 10, 3),
            språk = "NO"
        )

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentPåbegyntSøknad("ingensoknad") } returns null
                    every { it.hentPåbegyntSøknad("harsoknad") } returns expectedSoknad
                }
            )
        ) {
            autentisert(
                endepunkt = "${Configuration.basePath}/soknad/paabegynt",
                token = TestApplication.getToken("harsoknad"),
                httpMethod = HttpMethod.Get,
            ).apply {
                val expectedJson = """{"uuid":"258b2f1b-bdda-4bed-974c-c4ddb206e4f4","startDato":"2021-10-03","språk":"NO"}"""
                assertEquals(HttpStatusCode.OK, this.status)
                assertEquals(expectedJson, this.bodyAsText().trimIndent())
            }
        }
    }

    @Test
    fun `Returnerer 404 når søker ikke har en påbegynt søknad`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentPåbegyntSøknad("ingensoknad") } returns null
                }
            )
        ) {
            autentisert(
                endepunkt = "${Configuration.basePath}/soknad/paabegynt",
                token = TestApplication.getToken("ingensoknad"),
                httpMethod = HttpMethod.Get,
            ).apply {
                assertEquals(HttpStatusCode.NotFound, this.status)
            }
        }
    }
}
