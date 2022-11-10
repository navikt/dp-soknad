package no.nav.dagpenger.soknad.personalia

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class KontonummerOppslagTest {
    @Test
    fun `tomt kontonummmer dersom bank konto ikke finnes`() {
        val kontonummerOppslag =
            KontonummerOppslag(
                dpProxyUrl = "http://localhost",
                tokenProvider = { "token" },
                MockEngine() {
                    respondError(HttpStatusCode.NotFound)
                }
            )
        runBlocking {
            kontonummerOppslag.hentKontonummer("").let {
                assertEquals(Kontonummer(), it)
            }
        }
    }

    @Test
    fun `Happy path`() {
        val kontonummerOppslag =
            KontonummerOppslag(
                dpProxyUrl = "http://localhost",
                tokenProvider = { "token" },
                MockEngine() {
                    //language=JSON
                    respond(
                        """{"kontonummer":"123"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            )
        runBlocking {
            assertEquals(kontonummerOppslag.hentKontonummer("").kontonummer, "123")
        }
    }
}
