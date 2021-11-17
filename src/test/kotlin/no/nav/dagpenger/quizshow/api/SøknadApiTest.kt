package no.nav.dagpenger.quizshow.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.dagpenger.quizshow.api.helpers.JwtStub
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

internal class SøknadApiTest {
    private val testIssuer = "test-issuer"
    private val jwtStub = JwtStub(testIssuer)
    private val clientId = "id"
    private val jackson = jacksonObjectMapper()
    private val rettighetsAvklaringer = mutableListOf<ØnskerRettighetsavklaringMelding>()
    private val svar = mutableListOf<FaktumSvar>()
    private val store = testStore()

    @Test
    fun `Skal starte søknad`() {

        withTestApplication({
            søknadApi(
                jwtStub.stubbedJwkProvider(),
                testIssuer,
                clientId,
                store
            )
        }) {
            autentisert(
                "${Configuration.basePath}/soknad",
                httpMethod = HttpMethod.Post,
                token = jwtStub.createTokenFor("12345678901", "id")
            ).apply {
                assertEquals(HttpStatusCode.Created, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
                val content = jackson.readTree(this.response.content)
                assertDoesNotThrow { content["søknad_uuid"].asText().also { UUID.fromString(it) } }
                assertEquals(1, rettighetsAvklaringer.size)
                assertEquals(content["søknad_uuid"].asText(), rettighetsAvklaringer.first().søknadUuid().toString())
                val rettighetsavklaring = jackson.readTree(rettighetsAvklaringer.first().toJson())
                assertEquals("12345678901", rettighetsavklaring["fødselsnummer"].asText())
            }
        }
    }

    @Test
    fun `Skal hente søknad seksjoner`() {

        withTestApplication({
            søknadApi(
                jwtStub.stubbedJwkProvider(),
                testIssuer,
                clientId,
                store
            )
        }) {
            autentisert(
                "${Configuration.basePath}/soknad/d172a832-4f52-4e1f-ab5f-8be8348d9280/neste-seksjon",
            ).apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun `404 på ting som ikke finnes`() {

        withTestApplication({
            søknadApi(
                jwtStub.stubbedJwkProvider(),
                testIssuer,
                clientId, store
            )
        }) {
            autentisert(
                "${Configuration.basePath}/soknad/12121/neste-seksjon"
            ).apply {
                assertEquals(HttpStatusCode.NotFound, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun `Skal hente søknad subsumsjoner`() {

        withTestApplication({
            søknadApi(
                jwtStub.stubbedJwkProvider(),
                testIssuer,
                clientId, store
            )
        }) {
            autentisert(
                "${Configuration.basePath}/soknad/d172a832-4f52-4e1f-ab5f-8be8348d9280/subsumsjoner"
            ).apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun `Skal kunne lagre faktum`() {

        withTestApplication({
            søknadApi(
                jwtStub.stubbedJwkProvider(),
                testIssuer,
                clientId, store
            )
        }) {
            autentisert(
                "${Configuration.basePath}/soknad/d172a832-4f52-4e1f-ab5f-8be8348d9280/faktum/1245",
                httpMethod = HttpMethod.Put,
                body = """{"type": "boolean", "svar": true}"""
            ).apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals(1, svar.size)
            }
        }
    }

    private fun testStore() = object : SøknadStore {

        //language=JSON
        private val søkerOppgave =
            """
      {
        "@event_name": "søker_oppgave",
        "@id": "f1387052-1132-4692-be23-803817bdf214",
        "@opprettet": "2021-11-01T14:18:34.039275",
        "søknad_uuid": "d172a832-4f52-4e1f-ab5f-8be8348d9280",
        "seksjon_navn": "gjenopptak",
        "indeks": 0,
        "identer": [
          {
            "id": "123456789",
            "type": "folkeregisterident",
            "historisk": false
          }
        ],
        "fakta": [
          {
            "navn": "Har du hatt dagpenger siste 52 uker?",
            "id": "1",
            "roller": [
              "søker"
            ],
            "type": "boolean",
            "godkjenner": []
          }
        ],
        "subsumsjoner": [
          {
            "lokalt_resultat": null,
            "navn": "Sjekk at `Har du hatt dagpenger siste 52 uker med id 1` er lik true",
            "forklaring": "saksbehandlerforklaring",
            "type": "Enkel subsumsjon",
            "fakta": [
              "1"
            ]
          }
        ]
      }
            """.trimIndent()

        override fun håndter(rettighetsavklaringMelding: ØnskerRettighetsavklaringMelding) {
            rettighetsAvklaringer.add(rettighetsavklaringMelding)
        }

        override fun håndter(faktumSvar: FaktumSvar) {
            svar.add(faktumSvar)
        }

        override fun hent(søknadUuid: String): String? =
            if (søknadUuid == "d172a832-4f52-4e1f-ab5f-8be8348d9280") søkerOppgave else null
    }

    private fun TestApplicationEngine.autentisert(
        endepunkt: String,
        token: String = jwtStub.createTokenFor("test@nav.no", "id"),
        httpMethod: HttpMethod = HttpMethod.Get,
        body: String? = null
    ) = handleRequest(httpMethod, endepunkt) {
        addHeader(
            HttpHeaders.Accept,
            ContentType.Application.Json.toString()
        )
        addHeader(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString()
        )
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        body?.also { setBody(it) }
    }
}
