package no.nav.dagpenger.soknad.personalia

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

internal class KontonummerOppslagTest {
    @Test
    fun `tomt kontonummmer dersom bank konto ikke finnes`() {
        val kontonummerOppslag =
            KontonummerOppslag(
                kontoRegisterUrl = "http://localhost",
                tokenProvider = { "token" },
                MockEngine() {
                    respondError(HttpStatusCode.NotFound)
                },
            )
        runBlocking {
            assertEquals(Kontonummer.TOM, kontonummerOppslag.hentKontonummer(""))
        }
    }

    @Test
    fun `Happy path`() {
        val kontonummerOppslag =
            KontonummerOppslag(
                kontoRegisterUrl = "http://localhost",
                tokenProvider = { subjektToken -> "token $subjektToken" },
                httpClientEngine = MockEngine() { request ->
                    when (request.headers["Authorization"]?.split(" ")?.last()) {
                        "norskkonto" -> {
                            respond(
                                content = testNorskKontoResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        "utenlandskkonto" -> {
                            respond(
                                content = utenlandskKontoResponse,
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        }
                        else -> fail()
                    }
                },
            )
        runBlocking {
            assertEquals(kontonummerOppslag.hentKontonummer("norskkonto"), Kontonummer(kontonummer = "123"))
            assertEquals(
                kontonummerOppslag.hentKontonummer("utenlandskkonto"),
                Kontonummer(
                    kontonummer = "456",
                    banknavn = "banknavn",
                    bankLandkode = "SE",
                ),
            )
        }
    }

    @Language("JSON")
    private val utenlandskKontoResponse = """
          {
            "utenlandskKontoInfo": {
              "banknavn": "banknavn",
              "bankkode": "456",
              "bankLandkode": "SE",
              "valutakode": "SEK",
              "swiftBicKode": "SHEDNO22",
              "bankadresse1": "string",
              "bankadresse2": "string",
              "bankadresse3": "string"
            }
          }
    """.trimIndent()

    @Language("JSON")
    private val testNorskKontoResponse = """
        {
          "kontonummer": "123"
        }
    """.trimIndent()
}
