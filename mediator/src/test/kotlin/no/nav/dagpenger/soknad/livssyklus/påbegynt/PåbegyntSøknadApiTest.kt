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
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class PåbegyntSøknadApiTest {

    @Test
    fun `Skal hente påbegynt søknad`() {
        val expectedIdent = "12345678901"
        val søknadUUID = "258b2f1b-bdda-4bed-974c-c4ddb206e4f4"
        val opprettet = ZonedDateTime.of(
            2022, 1, 1, 23, 23, 23, 11, ZoneId.of("UTC+2")
        )
        val expectedSoknad = Søknad(
            UUID.fromString(søknadUUID),
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
                assertEquals(HttpStatusCode.OK, this.status)
                val response = objectMapper.readTree(this.bodyAsText())
                assertEquals(søknadUUID, response["uuid"].asText())
                assertEquals("no", response["spraak"].asText())
                assertEquals(opprettet.toString(), response["opprettet"].asText())
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
