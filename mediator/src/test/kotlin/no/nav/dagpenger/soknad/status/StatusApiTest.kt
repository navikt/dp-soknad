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
import java.time.LocalDateTime
import java.util.UUID

class StatusApiTest {
    private val søknadUuid = UUID.randomUUID()
    private val endepunkt = "${Configuration.basePath}/soknad/$søknadUuid/status"

    @Test
    fun `status paabegynt`() {
        val opprettet = LocalDateTime.MAX
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentTilstand(søknadUuid) } returns Påbegynt
                    every { it.hentOpprettet(søknadUuid) } returns opprettet
                }
            )
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson = """{"status":"Paabegynt","soknadOpprettet":"$opprettet"}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `returnerer status til søknad`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentTilstand(søknadUuid) } returnsMany listOf(UnderOpprettelse, Innsendt, Slettet)
                    every { it.hentOpprettet(søknadUuid) } returns LocalDateTime.MAX
                }
            )
        ) {
            assertUnderOpprettelse(statuskode = HttpStatusCode.InternalServerError)

            assertSøknadTilstand(Innsendt, HttpStatusCode.OK)
            assertSøknadTilstand(Slettet, HttpStatusCode.NotFound)
        }
    }

    private suspend fun ApplicationTestBuilder.assertUnderOpprettelse(statuskode: HttpStatusCode) {
        autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
            assertEquals(statuskode, this.status)
        }
    }

    @Test
    fun `Skal avvise autentiserte kall der pid på token ikke er eier av søknaden`() {
        val mediatorMock = mockk<SøknadMediator>().also {
            every { it.hentEier(søknadUuid) } returns "annen eier"
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(søknadMediator = mediatorMock)
        ) {
            assertEquals(HttpStatusCode.Forbidden, autentisert(endepunkt).status)
        }
    }

    @Test
    fun `returnerer 404 når søknad ikke eksisterer`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentTilstand(søknadUuid) } returns null
                    every { it.hentOpprettet(søknadUuid) } returns LocalDateTime.MAX
                }
            )
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.NotFound, this.status)
            }
        }
    }

    private suspend fun ApplicationTestBuilder.assertSøknadTilstand(
        tilstand: Søknad.Tilstand.Type,
        expectedStatusCode: HttpStatusCode,
    ) {
        autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
            assertEquals(expectedStatusCode, this.status)
            val expectedJson = """{"tilstand":"$tilstand"}"""
            assertEquals(expectedJson, this.bodyAsText())
            assertEquals("application/json; charset=UTF-8", this.contentType().toString())
        }
    }
}
