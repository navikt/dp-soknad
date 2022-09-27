package no.nav.dagpenger.soknad

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.soknad.Søknadsprosess.NySøknadsProsess
import no.nav.dagpenger.soknad.Søknadsprosess.PåbegyntSøknadsProsess
import no.nav.dagpenger.soknad.TestApplication.autentisert
import no.nav.dagpenger.soknad.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.livssyklus.påbegynt.FaktumSvar
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.soknad.mal.SøknadMal
import no.nav.dagpenger.soknad.mal.testSøknadMalMelding
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID

internal class SøknadApiTest {
    private val dummyUuid = UUID.randomUUID()

    @Test
    fun `Skal starte utfylling av søknad`() {
        val egenvalgtSpråk = slot<String>()
        val defaultSpråk = slot<String>()
        val søknadMediatorMock = mockk<SøknadMediator>().also {
            every {
                it.hentEllerOpprettSøknadsprosess(
                    defaultDummyFodselsnummer,
                    capture(egenvalgtSpråk)
                )
            } returns NySøknadsProsess()
            every {
                it.hentEllerOpprettSøknadsprosess(
                    "12345678910",
                    capture(defaultSpråk)
                )
            } returns PåbegyntSøknadsProsess(
                UUID.randomUUID()
            )
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = søknadMediatorMock
            )
        ) {
            autentisert(
                "${Configuration.basePath}/soknad?spraak=NY",
                httpMethod = HttpMethod.Post
            ).apply {
                assertEquals(HttpStatusCode.Created, this.status)
                assertNotNull(this.headers[HttpHeaders.Location])
                assertEquals("NY", egenvalgtSpråk.captured)
            }

            autentisert(
                endepunkt = "${Configuration.basePath}/soknad",
                token = TestApplication.getTokenXToken("12345678910"),
                httpMethod = HttpMethod.Post
            ).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                assertNotNull(this.headers[HttpHeaders.Location])
                assertEquals("NB", defaultSpråk.captured)
            }
        }
    }

    @Test
    fun `Ferdigstill søknad`() {
        val testSøknadUuid = UUID.randomUUID()
        val slot = slot<SøknadInnsendtHendelse>()
        val søknadMediatorMock = mockk<SøknadMediator>().also {
            justRun {
                it.behandle(capture(slot))
                it.lagreSøknadsTekst(testSøknadUuid, any())
            }
            every { it.hentEier(any()) } returns defaultDummyFodselsnummer
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = søknadMediatorMock
            )
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/$testSøknadUuid/ferdigstill",
                httpMethod = HttpMethod.Put,
                body = "{}"
            ).apply {
                assertEquals(HttpStatusCode.NoContent, this.status)
            }
        }
        verify(exactly = 1) { søknadMediatorMock.behandle(any<SøknadInnsendtHendelse>()) }
        assertTrue(slot.isCaptured)
        assertEquals(testSøknadUuid, slot.captured.søknadID())
        assertEquals(defaultDummyFodselsnummer, slot.captured.ident())
    }

    @Test
    fun `Kan bare sende json`() {
        val testSøknadUuid = UUID.randomUUID()
        val søknadMediatorMock = mockk<SøknadMediator>().also {
            every { it.hentEier(any()) } returns defaultDummyFodselsnummer
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = søknadMediatorMock
            )
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/$testSøknadUuid/ferdigstill",
                httpMethod = HttpMethod.Put,
                body = "Det her er ihvertall ikke json for å si det sånn"
            ).apply {
                assertEquals(HttpStatusCode.BadRequest, this.status)
            }
        }
    }

    @Test
    fun `Skal hente søknad fakta`() {
        val testSøknadUuid = UUID.randomUUID()
        // language=JSON
        val frontendformat = """{"id":"blabla"}"""
        val søkerOppgave = mockk<SøkerOppgave>().also {
            every { it.asFrontendformat() } returns objectMapper.readTree(frontendformat)
        }
        val mockSøknadMediator = mockk<SøknadMediator>().also { søknadMediator ->
            every { søknadMediator.hentSøkerOppgave(testSøknadUuid) } returns søkerOppgave
            every { søknadMediator.hentEier(testSøknadUuid) } returns defaultDummyFodselsnummer
        }
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockSøknadMediator
            )
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/$testSøknadUuid/neste"
            ).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                assertEquals("application/json; charset=UTF-8", this.headers["Content-Type"])
                assertEquals(frontendformat, this.bodyAsText())
            }
        }
    }

    @Test
    fun `Skal avvise uautoriserte pga tokenx pid ikke eier søknad`() {
        val søknadId = UUID.randomUUID()

        val mockSøknadMediator = mockk<SøknadMediator>().also { søknadMediator ->
            every { søknadMediator.hentEier(søknadId) } returns "hubba"
        }
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockSøknadMediator
            )
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/$søknadId/neste"
            ).let {
                assertEquals(HttpStatusCode.Forbidden, it.status)
                assertEquals("application/json; charset=UTF-8", it.headers["Content-Type"])
            }

            autentisert(
                "${Configuration.basePath}/soknad/$søknadId/ferdigstill",
                httpMethod = HttpMethod.Put,
                body = "{}"
            ).let {
                assertEquals(HttpStatusCode.Forbidden, it.status)
                assertEquals("application/json; charset=UTF-8", it.headers["Content-Type"])
            }

            autentisert(
                "${Configuration.basePath}/soknad/$søknadId/faktum/3000",
                httpMethod = HttpMethod.Put,
                body = """{"type": "boolean", "svar": true}"""
            ).let {
                assertEquals(HttpStatusCode.Forbidden, it.status)
                assertEquals("application/json; charset=UTF-8", it.headers["Content-Type"])
            }

            autentisert(
                "${Configuration.basePath}/soknad/$søknadId",
                httpMethod = HttpMethod.Delete
            ).let {
                assertEquals(HttpStatusCode.Forbidden, it.status)
                assertEquals("application/json; charset=UTF-8", it.headers["Content-Type"])
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
                assertEquals(HttpStatusCode.NotFound, this.status)
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "1234    | 200",
        "1234.1  | 200",
        "1234.XX | 400",
        "blabla  | 400",
        delimiter = '|'
    )
    fun `Skal kunne lagre faktum`(id: String, status: Int) {
        val søknadId = UUID.randomUUID()
        val mockSøknadMediator = mockk<SøknadMediator>().also {
            justRun {
                it.behandle(any<FaktumSvar>())
            }
            every { it.hentEier(søknadId) } returns defaultDummyFodselsnummer
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(søknadMediator = mockSøknadMediator)
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/$søknadId/faktum/$id",
                httpMethod = HttpMethod.Put,
                body = """{"type": "boolean", "svar": true}"""
            ).apply {
                val expectedStatusCode = HttpStatusCode.fromValue(status)
                assertEquals(expectedStatusCode, this.status)
                assertEquals("application/json; charset=UTF-8", this.headers["Content-Type"])
                if (expectedStatusCode.isSuccess()) {
                    verify(exactly = 1) { mockSøknadMediator.behandle(any<FaktumSvar>()) }
                }
            }
        }
    }

    @Test
    fun `Skal kunne slette påbegynt søknad`() {
        val søknadUuid = UUID.randomUUID()
        val mockSøknadMediator = mockk<SøknadMediator>().also {
            justRun {
                it.behandle(any<SlettSøknadHendelse>())
            }
            every { it.hentEier(søknadUuid) } returns defaultDummyFodselsnummer
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(søknadMediator = mockSøknadMediator)
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/$søknadUuid",
                httpMethod = HttpMethod.Delete
            ).apply {
                assertEquals(HttpStatusCode.OK, this.status)
            }
        }

        verify(exactly = 1) {
            mockSøknadMediator.behandle(
                SlettSøknadHendelse(
                    søknadUuid,
                    ident = defaultDummyFodselsnummer
                )
            )
        }
    }

    @ParameterizedTest
    @CsvSource(
        "boolean | true",
        "boolean | false",
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
        val søknadId = UUID.randomUUID()
        val faktumSvar = slot<FaktumSvar>()
        val mockSøknadMediator = mockk<SøknadMediator>().also {
            justRun { it.behandle(capture(faktumSvar)) }
            every { it.hentEier(søknadId) } returns defaultDummyFodselsnummer
        }
        val jsonSvar = """{"type": "$type", "svar": $svar}"""

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(søknadMediator = mockSøknadMediator)
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/$søknadId/faktum/1245",
                httpMethod = HttpMethod.Put,
                body = jsonSvar
            ).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                assertEquals("application/json; charset=UTF-8", this.headers["Content-Type"])
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
        TestApplication.withMockAuthServerAndTestApplication {
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("${Configuration.basePath}/soknad/mal").status
            )
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
                assertEquals(HttpStatusCode.NotFound, this.status)
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
                assertEquals(HttpStatusCode.BadRequest, this.status)
            }
        }
    }

    @Test
    fun `Skal kunne hente søknadmal`() {
        val meditatorMock = mockk<SøknadMediator>().also {
            every { it.hentNyesteMal("Dagpenger") } returns
                SøknadMal(
                    prosessnavn = "Dagpenger",
                    prosessversjon = 1,
                    mal = objectMapper.readTree(testSøknadMalMelding())
                )
        }
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = meditatorMock
            )
        ) {
            autentisert(
                httpMethod = HttpMethod.Get,
                endepunkt = "${Configuration.basePath}/soknad/mal"
            ).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val malen = objectMapper.readTree(this.bodyAsText())
                assertTrue(malen.has("seksjoner"))
                assertEquals(0, malen["seksjoner"].size())
            }
        }
    }
}
