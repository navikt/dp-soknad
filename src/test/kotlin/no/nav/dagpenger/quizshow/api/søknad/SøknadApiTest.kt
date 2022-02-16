package no.nav.dagpenger.quizshow.api.søknad

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.SøknadStore
import no.nav.dagpenger.quizshow.api.TestApplication
import no.nav.dagpenger.quizshow.api.TestApplication.autentisert
import no.nav.dagpenger.quizshow.api.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID

internal class SøknadApiTest {
    private val dummyUuid = UUID.randomUUID()

    @Test
    fun `Skal starte søknad`() {
        val mockStore = mockk<SøknadStore>().also {
            justRun {
                it.håndter(any<NySøknadMelding>())
            }
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                store = mockStore
            )
        ) {
            autentisert(
                "${Configuration.basePath}/soknad",
                httpMethod = HttpMethod.Post,
            ).apply {
                assertEquals(HttpStatusCode.Created, this.response.status())
                assertNotNull(this.response.headers[HttpHeaders.Location])
            }
        }

        verify(exactly = 1) { mockStore.håndter(any<NySøknadMelding>()) }
    }

    @Test
    fun `Skal hente søknad fakta`() {
        val testJson = """{"key": "value"}"""
        val mockStore = mockk<SøknadStore>().also { soknadStore ->
            every { soknadStore.hentFakta("d172a832-4f52-4e1f-ab5f-8be8348d9280") } returns testJson
        }
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                store = mockStore
            )
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/d172a832-4f52-4e1f-ab5f-8be8348d9280/fakta",
            ).apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
                assertEquals(testJson, this.response.content)
            }
        }
    }

    @Test
    fun `404 på ting som ikke finnes`() {

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/12121/neste-seksjon"
            ).apply {
                assertEquals(HttpStatusCode.NotFound, this.response.status())
            }
        }
    }

    @Test
    fun `Skal kunne lagre faktum`() {
        val mockStore = mockk<SøknadStore>().also {
            justRun {
                it.håndter(any<FaktumSvar>())
            }
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(mockStore)
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/d172a832-4f52-4e1f-ab5f-8be8348d9280/faktum/1245",
                httpMethod = HttpMethod.Put,
                body = """{"type": "boolean", "svar": true}"""
            ).apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
            }
        }

        verify(exactly = 1) { mockStore.håndter(any<FaktumSvar>()) }
    }

    @ParameterizedTest
    @CsvSource(
        "boolean | true",
        """localdate | "2022-01-15"""",
        "double | 3.0",
        """envalg | "valg1"""",
        """flervalg | ["valg1"]""",
        """int | 5""",
        """periode | {"fom":"2022-01-15","tom":"2022-01-29"}""",
        """tekst | "en tekst"""",
        """land | "NOR"""",
        delimiter = '|'
    )
    fun `test faktum svar typer`(type: String, svar: String) {
        val faktumSvar = slot<FaktumSvar>()
        val mockStore = mockk<SøknadStore>().also {
            justRun { it.håndter(capture(faktumSvar)) }
        }

        val jsonSvar = """{"type": "$type", "svar": $svar}"""

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(mockStore)
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/d172a832-4f52-4e1f-ab5f-8be8348d9280/faktum/1245",
                httpMethod = HttpMethod.Put,
                body = jsonSvar
            ).apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
            }
        }

        val svarSomJson = objectMapper.readTree(faktumSvar.captured.toJson())
        val fakta = svarSomJson["fakta"]
        val førsteFaktasvar = fakta[0]
        assertEquals("1245", førsteFaktasvar["id"].asText())
        assertEquals(type, førsteFaktasvar["type"].asText())
        assertEquals(svar, førsteFaktasvar["svar"].toString())
    }

    @Test
    fun `Skal avvise uautentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            handleRequest(HttpMethod.Get, "${Configuration.basePath}/soknad/$dummyUuid/fakta") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }.apply {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `Kommer ikke med faktum id`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            autentisert(
                httpMethod = HttpMethod.Put,
                endepunkt = "${Configuration.basePath}/soknad/$dummyUuid/faktum/",
                body = """{"type":"boolean","svar":true}"""
            ).apply {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    @Test
    fun `Kommer ikke med uglyldig faktum id`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            autentisert(
                httpMethod = HttpMethod.Put,
                endepunkt = "${Configuration.basePath}/soknad/$dummyUuid/faktum/blabla",
                body = """{"type":"boolean","svar":true}"""
            ).apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        }
    }
}
