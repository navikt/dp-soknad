package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.SøknadStore
import no.nav.dagpenger.quizshow.api.TestApplication
import no.nav.dagpenger.quizshow.api.TestApplication.autentisert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadApiTest {
    private val jackson = jacksonObjectMapper()
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
    fun `Godta svar med gyldig input for Valg`() {
        val gyldigSvar = Svar("boolean", true)
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            autentisert(
                httpMethod = HttpMethod.Put,
                endepunkt = "${Configuration.basePath}/soknad/$dummyUuid/faktum/456)",
                body = jackson.writeValueAsString(gyldigSvar)
            ).apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `Avvis svar av typen Valg dersom ingen alternativer er valgt`() {
        val ugyldigSvar = Svar("valg", emptyList<String>())
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            autentisert(
                httpMethod = HttpMethod.Put,
                endepunkt = "${Configuration.basePath}/soknad/$dummyUuid/faktum/456)",
                body = jackson.writeValueAsString(ugyldigSvar)
            ).apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        }
    }

//    private fun testStore() = object : SøknadStore {
//
//        //language=JSON
//        private val søkerOppgave =
//            """
//      {
//        "@event_name": "søker_oppgave",
//        "@id": "f1387052-1132-4692-be23-803817bdf214",
//        "@opprettet": "2021-11-01T14:18:34.039275",
//        "søknad_uuid": "d172a832-4f52-4e1f-ab5f-8be8348d9280",
//        "seksjon_navn": "gjenopptak",
//        "indeks": 0,
//        "identer": [
//          {
//            "id": "123456789",
//            "type": "folkeregisterident",
//            "historisk": false
//          }
//        ],
//        "fakta": [
//          {
//            "navn": "Har du hatt dagpenger siste 52 uker?",
//            "id": "1",
//            "roller": [
//              "søker"
//            ],
//            "type": "boolean",
//            "godkjenner": []
//          }
//        ],
//        "subsumsjoner": [
//          {
//            "lokalt_resultat": null,
//            "navn": "Sjekk at `Har du hatt dagpenger siste 52 uker med id 1` er lik true",
//            "forklaring": "saksbehandlerforklaring",
//            "type": "Enkel subsumsjon",
//            "fakta": [
//              "1"
//            ]
//          }
//        ]
//      }
//            """.trimIndent()
//
//        override fun håndter(faktumSvar: FaktumSvar) {
//            svar.add(faktumSvar)
//        }
//
//        override fun håndter(nySøknadMelding: NySøknadMelding) {
//            // TODO("Not yet implemented")
//        }
//
//        override fun hentFakta(søknadUuid: String): String? {
//            //language=JSON
//            return """
//                [
//                {
//                "id": "1.1",
//                "beskrivendeId": "id",
//                "type": "boolean",
//                "svar" : true
//                }
//                ]
//            """.trimIndent()
//        }
//    }
}
