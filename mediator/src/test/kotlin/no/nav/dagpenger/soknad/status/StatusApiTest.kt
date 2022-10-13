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
import no.nav.dagpenger.soknad.status.SøknadStatus.Paabegynt
import no.nav.dagpenger.soknad.status.SøknadStatus.UnderBehandling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class StatusApiTest {
    private val søknadUuid = UUID.randomUUID()
    private val endepunkt = "${Configuration.basePath}/soknad/$søknadUuid/status"

    @Test
    fun `Status på søknad med tilstand Påbegynt`() {
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
                val expectedJson = """{"status":"$Paabegynt","opprettet":"$opprettet"}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `Status på søknad med tilstand Innsendt`() {
        val opprettet = LocalDateTime.MAX
        val innsendt = LocalDateTime.MAX
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentTilstand(søknadUuid) } returns Innsendt
                    every { it.hentOpprettet(søknadUuid) } returns opprettet
                }
            )
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson = """{"status":"$UnderBehandling","opprettet":"$opprettet","innsendt":"$innsendt"}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `Status på søknad med tilstand UnderOpprettelse`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentTilstand(søknadUuid) } returns UnderOpprettelse
                    every { it.hentOpprettet(søknadUuid) } returns LocalDateTime.MAX
                }
            )
        ) {
            assertStatuskode(expected = HttpStatusCode.InternalServerError)
        }
    }

    @Test
    fun `Status på søknad med tilstand Slettet`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentTilstand(søknadUuid) } returns Slettet
                    every { it.hentOpprettet(søknadUuid) } returns LocalDateTime.MAX
                }
            )
        ) {
            assertStatuskode(expected = HttpStatusCode.NotFound)
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

    private suspend fun ApplicationTestBuilder.assertStatuskode(expected: HttpStatusCode) {
        autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
            assertEquals(expected, this.status)
        }
    }
}
