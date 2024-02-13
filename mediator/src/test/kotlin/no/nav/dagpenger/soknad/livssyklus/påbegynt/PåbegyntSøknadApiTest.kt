package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.UUID

class PåbegyntSøknadApiTest {
    @Test
    fun `Skal hente påbegynt søknad`() {
        val expectedIdent = "12345678901"
        val søknadId = UUID.fromString("258b2f1b-bdda-4bed-974c-c4ddb206e4f4")
        val expectedSoknad = Søknad(
            søknadId,
            Språk("NO"),
            expectedIdent,
        ).also {
            it.håndterSøknadOpprettetHendelse(
                SøknadOpprettetHendelse(
                    Prosessversjon("test", 1),
                    søknadId,
                    expectedIdent,
                ),
            )
            it.håndterFaktumOppdatertHendelse(
                FaktumOppdatertHendelse(
                    søknadId,
                    expectedIdent,
                ),
            )
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentPåbegyntSøknad("ingensoknad") } returns null
                    every { it.hentPåbegyntSøknad("harsoknad") } returns expectedSoknad
                },
            ),
        ) {
            autentisert(
                endepunkt = "${Configuration.basePath}/soknad/paabegynt",
                token = TestApplication.getTokenXToken("harsoknad"),
                httpMethod = HttpMethod.Get,
            ).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val response = objectMapper.readTree(this.bodyAsText())
                assertEquals(søknadId.toString(), response["uuid"].asText())
                assertEquals("no", response["spraak"].asText())
                assertFalse(response["opprettet"].isNull)
                assertFalse(response["sistEndret"].isNull)
            }
        }
    }

    @Test
    fun `Returnerer 404 når søker ikke har en påbegynt søknad`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentPåbegyntSøknad("ingensoknad") } returns null
                },
            ),
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
