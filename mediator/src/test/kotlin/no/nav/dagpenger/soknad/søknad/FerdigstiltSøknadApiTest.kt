package no.nav.dagpenger.soknad.søknad

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.db.FerdigstiltSøknadRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FerdigstiltSøknadApiTest {
    private val dummySøknadsTekst = """{"key": "value"}"""
    private val dummyFakta = """{"fakta1": "value1"}"""

    @Test
    fun `henter tekst`() {
        val søknadId = UUID.randomUUID()
        testApplication {
            application {
                routing {
                    ferdigstiltSøknadsApi(
                        mockk<FerdigstiltSøknadRepository>().also {
                            every { it.hentTekst(søknadId) } returns dummySøknadsTekst
                        }
                    )
                }
            }

            client.get("/$søknadId/ferdigstilt/tekst").also { response ->
                assertJsonEquals(dummySøknadsTekst, response.bodyAsText())
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())
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
                        mockk<FerdigstiltSøknadRepository>().also {
                            every { it.hentFakta(søknadId) } returns dummyFakta
                        }
                    )
                }
            }

            client.get("/$søknadId/ferdigstilt/fakta").also { response ->
                assertJsonEquals(dummyFakta, response.bodyAsText())
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())
            }
        }
    }

    private fun assertJsonEquals(expected: String, actual: String) {
        fun String.removeWhitespace(): String = this.replace("\\s".toRegex(), "")
        Assertions.assertEquals(expected.removeWhitespace(), actual.removeWhitespace())
    }
}
