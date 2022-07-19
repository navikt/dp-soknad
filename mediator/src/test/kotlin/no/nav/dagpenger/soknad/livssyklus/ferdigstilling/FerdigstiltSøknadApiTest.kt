package no.nav.dagpenger.soknad.livssyklus.ferdigstilling

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FerdigstiltSøknadApiTest {
    private val dummySøknadsTekst = """{"key": "value"}"""
    private val dummyFakta = """{"fakta1": "value1"}"""

    @Test
    fun `Skal avvise uautentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication() {
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("${Configuration.basePath}/${UUID.randomUUID()}/ferdigstilt/tekst").status
            )
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("${Configuration.basePath}/${UUID.randomUUID()}/ferdigstilt/fakta").status
            )
        }
    }

    @Test
    fun `autentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication() {
            assertEquals(
                HttpStatusCode.OK,
                autentisert(
                    endepunkt = "${Configuration.basePath}/${UUID.randomUUID()}/ferdigstilt/tekst"
                ).status
            )
            assertEquals(
                HttpStatusCode.OK,
                autentisert(
                    endepunkt = "${Configuration.basePath}/${UUID.randomUUID()}/ferdigstilt/tekst"
                ).status
            )
        }
    }

    @Test
    fun `henter tekst`() {
        val søknadId = UUID.randomUUID()
        testApplication {
            application {
                routing {
                    ferdigstiltSøknadsApi(
                        mockk<FerdigstiltSøknadPostgresRepository>().also {
                            every { it.hentTekst(søknadId) } returns dummySøknadsTekst
                        }
                    )
                }
            }

            client.get("${Configuration.basePath}/$søknadId/ferdigstilt/tekst").also { response ->
                assertJsonEquals(dummySøknadsTekst, response.bodyAsText())
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())
            }
        }
    }

    @Test
    fun `returnerer 404 når tekst ikke finnes`() {
        val søknadId = UUID.randomUUID()
        testApplication {
            application {
                routing {
                    ferdigstiltSøknadsApi(
                        mockk<FerdigstiltSøknadPostgresRepository>().also {
                            every { it.hentTekst(søknadId) } throws NotFoundException(søknadId.toString())
                        }
                    )
                }
            }

            client.get("${Configuration.basePath}/$søknadId/ferdigstilt/tekst").also { response ->
                assertEquals(HttpStatusCode.NotFound, response.status)
            }
        }
    }

    @Test
    fun `henter fakta`() {
        val søknadId = UUID.randomUUID()
        testApplication {
            application {
                routing {
                    ferdigstiltSøknadsApi(
                        mockk<FerdigstiltSøknadPostgresRepository>().also {
                            every { it.hentFakta(søknadId) } returns dummyFakta
                        }
                    )
                }
            }

            client.get("${Configuration.basePath}/$søknadId/ferdigstilt/fakta").also { response ->
                assertJsonEquals(dummyFakta, response.bodyAsText())
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())
            }
        }
    }

    @Test
    fun `returnerer 404 når fakta ikke finnes`() {
        val søknadId = UUID.randomUUID()
        testApplication {
            application {
                routing {
                    ferdigstiltSøknadsApi(
                        mockk<FerdigstiltSøknadPostgresRepository>().also {
                            every { it.hentFakta(søknadId) } throws NotFoundException(søknadId.toString())
                        }
                    )
                }
            }

            client.get("${Configuration.basePath}/$søknadId/ferdigstilt/fakta").also { response ->
                assertEquals(HttpStatusCode.NotFound, response.status)
            }
        }
    }

    private fun assertJsonEquals(expected: String, actual: String) {
        fun String.removeWhitespace(): String = this.replace("\\s".toRegex(), "")
        assertEquals(expected.removeWhitespace(), actual.removeWhitespace())
    }
}
