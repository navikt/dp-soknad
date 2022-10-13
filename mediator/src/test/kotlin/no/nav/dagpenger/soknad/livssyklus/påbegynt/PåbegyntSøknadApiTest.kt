package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class PåbegyntSøknadApiTest {

    @Test
    fun `Skal hente påbegynt søknad`() {
        val expectedIdent = "12345678901"
        val expectedSoknad = Søknad(
            UUID.fromString("258b2f1b-bdda-4bed-974c-c4ddb206e4f4"),
            Språk("NO"),
            expectedIdent,
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
                token = TestApplication.getTokenXToken("harsoknad"),
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
                token = TestApplication.getTokenXToken("ingensoknad"),
                httpMethod = HttpMethod.Get,
            ).apply {
                assertEquals(HttpStatusCode.NotFound, this.status)
            }
        }
    }
}
