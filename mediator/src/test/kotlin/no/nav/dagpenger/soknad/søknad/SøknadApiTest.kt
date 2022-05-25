package no.nav.dagpenger.soknad.søknad

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.Søknadsprosess.NySøknadsProsess
import no.nav.dagpenger.soknad.Søknadsprosess.PåbegyntSøknadsProsess
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import no.nav.dagpenger.soknad.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.soknad.db.PåbegyntSøknad
import no.nav.dagpenger.soknad.db.SøknadMal
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.mottak.testSøknadMalMelding
import no.nav.dagpenger.soknad.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.util.UUID

internal class SøknadApiTest {
    private val dummyUuid = UUID.randomUUID()

    @Test
    fun `Skal starte utfylling av søknad`() {
        val søknadMediatorMock = mockk<SøknadMediator>().also {
            every { it.hentEllerOpprettSøknadsprosess(defaultDummyFodselsnummer) } returns NySøknadsProsess()
            every { it.hentEllerOpprettSøknadsprosess("12345678910") } returns PåbegyntSøknadsProsess(
                UUID.randomUUID()
            )
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = søknadMediatorMock
            )
        ) {
            autentisert(
                "${Configuration.basePath}/soknad",
                httpMethod = HttpMethod.Post,
            ).apply {
                assertEquals(HttpStatusCode.Created, this.status)
                assertNotNull(this.headers[HttpHeaders.Location])
            }

            autentisert(
                endepunkt = "${Configuration.basePath}/soknad",
                token = TestApplication.getToken("12345678910"),
                httpMethod = HttpMethod.Post
            ).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                assertNotNull(this.headers[HttpHeaders.Location])
            }
        }
    }

    @Test
    fun `Skal hente påbegynte søknader`() {

        val expectedSoknader = listOf(
            PåbegyntSøknad(
                UUID.fromString("258b2f1b-bdda-4bed-974c-c4ddb206e4f4"),
                LocalDate.of(2021, 10, 3)
            )
        )
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentPåbegynte("ingensoknad") } returns emptyList()
                    every { it.hentPåbegynte("harsoknad") } returns expectedSoknader
                }
            )
        ) {
            autentisert(
                endepunkt = "${Configuration.basePath}/soknad/paabegynte",
                token = TestApplication.getToken("ingensoknad"),
                httpMethod = HttpMethod.Get,
            ).apply {
                val expectedJson = """[]"""
                assertEquals(HttpStatusCode.OK, this.status)
                assertEquals(expectedJson, this.bodyAsText().trimIndent())
            }

            autentisert(
                endepunkt = "${Configuration.basePath}/soknad/paabegynte",
                token = TestApplication.getToken("harsoknad"),
                httpMethod = HttpMethod.Get,
            ).apply {
                val expectedJson = """[{"uuid":"258b2f1b-bdda-4bed-974c-c4ddb206e4f4","startDato":"2021-10-03"}]"""
                assertEquals(HttpStatusCode.OK, this.status)
                assertEquals(expectedJson, this.bodyAsText().trimIndent())
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
        val søknadMediatorMock = mockk<SøknadMediator>()

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
        // language=JSON
        val frontendformat = """{"id":"blabla"}"""
        val søkerOppgave = mockk<SøkerOppgave>().also {
            every { it.eier() } returns defaultDummyFodselsnummer
            every { it.asFrontendformat() } returns objectMapper.readTree(frontendformat)
        }
        val mockSøknadMediator = mockk<SøknadMediator>().also { søknadMediator ->
            every { søknadMediator.hent(UUID.fromString("d172a832-4f52-4e1f-ab5f-8be8348d9280")) } returns søkerOppgave
        }
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockSøknadMediator
            )
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/d172a832-4f52-4e1f-ab5f-8be8348d9280/neste",
            ).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                assertEquals("application/json; charset=UTF-8", this.headers["Content-Type"])
                assertEquals(frontendformat, this.bodyAsText())
            }
        }
    }

    @Test
    fun `Skal avvise uautoriserte kall`() {
        val søkerOppgave = mockk<SøkerOppgave>().also {
            every { it.eier() } returns "en annen eier"
            every { it.asFrontendformat() } returns objectMapper.nullNode()
        }
        val id = "d172a832-4f52-4e1f-ab5f-8be8348d9280"
        val mockSøknadMediator = mockk<SøknadMediator>().also { søknadMediator ->
            every { søknadMediator.hent(UUID.fromString(id)) } returns søkerOppgave
        }
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockSøknadMediator
            )
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/$id/neste",
            ).apply {
                assertEquals(HttpStatusCode.Forbidden, this.status)
                assertEquals("application/json; charset=UTF-8", this.headers["Content-Type"])
            }
        }
    }

    @Test
    fun `405 på ting som ikke finnes`() {

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/12121/neste-seksjon"
            ).apply {
                assertEquals(HttpStatusCode.MethodNotAllowed, this.status)
            }
        }
    }

    @Test
    fun `Skal kunne lagre faktum`() {
        val mockSøknadMediator = mockk<SøknadMediator>().also {
            justRun {
                it.behandle(any<FaktumSvar>())
            }
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(søknadMediator = mockSøknadMediator)
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/d172a832-4f52-4e1f-ab5f-8be8348d9280/faktum/1245",
                httpMethod = HttpMethod.Put,
                body = """{"type": "boolean", "svar": true}"""
            ).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                assertEquals("application/json; charset=UTF-8", this.headers["Content-Type"])
            }
        }

        verify(exactly = 1) { mockSøknadMediator.behandle(any<FaktumSvar>()) }
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
        val faktumSvar = slot<FaktumSvar>()
        val mockSøknadMediator = mockk<SøknadMediator>().also {
            justRun { it.behandle(capture(faktumSvar)) }
        }

        val jsonSvar = """{"type": "$type", "svar": $svar}"""

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(søknadMediator = mockSøknadMediator)
        ) {
            autentisert(
                "${Configuration.basePath}/soknad/d172a832-4f52-4e1f-ab5f-8be8348d9280/faktum/1245",
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
        TestApplication.withMockAuthServerAndTestApplication() {
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
                assertEquals(HttpStatusCode.MethodNotAllowed, this.status)
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
