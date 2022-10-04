package no.nav.dagpenger.soknad.status

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Slettet
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class StatusApiTest {
    private val søknadUuid = UUID.randomUUID()
    private val endepunkt = "${Configuration.basePath}/soknad/$søknadUuid/status"

    @Test
    fun `returnerer status til søknad`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentTilstand(søknadUuid) } returnsMany listOf(UnderOpprettelse, Innsendt, Slettet)
                }
            )
        ) {
            assertSøknadStatus(UnderOpprettelse)
            assertSøknadStatus(Innsendt)
            assertSøknadStatus(Slettet)
        }
    }

    @Test
    fun `returnerer Paabegynt gitt tilstand er Påbegynt`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentTilstand(søknadUuid) } returns Påbegynt
                }
            )
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val jsonUtenÅ = """{"tilstand":"Paabegynt"}"""
                assertEquals(jsonUtenÅ, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `returnerer 404 når søknad ikke eksisterer`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentTilstand(søknadUuid) } returns null
                }
            )
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.NotFound, this.status)
            }
        }
    }

    private suspend fun ApplicationTestBuilder.assertSøknadStatus(tilstand: Søknad.Tilstand.Type) {
        autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
            assertEquals(HttpStatusCode.OK, this.status)
            val expectedJson = """{"tilstand":"$tilstand"}"""
            assertEquals(expectedJson, this.bodyAsText())
            assertEquals("application/json; charset=UTF-8", this.contentType().toString())
        }
    }
}
