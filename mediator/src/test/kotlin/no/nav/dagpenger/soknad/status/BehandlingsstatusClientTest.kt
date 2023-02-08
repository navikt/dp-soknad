package no.nav.dagpenger.soknad.status

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.time.LocalDate
import java.util.UUID

class BehandlingsstatusClientTest {
    private val testTokenProvider: (token: String, audience: String) -> String = { _, _ -> "testToken" }
    private val baseUrl = "http://baseurl"
    private val innsynAudience = "dp-innsyn"
    private val subjectToken = "subjectToken"

    @Test
    fun `Får deserialisert til BehandlingsstatusDto`() {
        val fom = LocalDate.now()
        val callId = UUID.randomUUID().toString()
        MDC.put("call-id", callId)
        runBlocking {
            val behandlingsstatusClient = BehandlingsstatusHttpClient(
                baseUrl,
                innsynAudience,
                testTokenProvider,
                engine = MockEngine { request ->
                    assertEquals("$baseUrl/behandlingsstatus?fom=$fom", request.url.toString())
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals(
                        "Bearer ${testTokenProvider.invoke(subjectToken, innsynAudience)}",
                        request.headers[HttpHeaders.Authorization]
                    )
                    assertEquals(callId, request.headers[HttpHeaders.XRequestId])
                    respond(
                        content = """{"behandlingsstatus":"UnderBehandling"}""".trimMargin(),
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            )

            assertEquals(
                BehandlingsstatusDto("UnderBehandling"),
                behandlingsstatusClient.hentBehandlingsstatus(fom, subjectToken)
            )
        }
    }

    @Test
    fun `InternalServerError resulterer i ukjent behandlingsstatus`() {
        runBlocking {
            val behandlingsstatusClient = BehandlingsstatusHttpClient(
                baseUrl,
                innsynAudience,
                testTokenProvider,
                engine = MockEngine {
                    this.respondError(status = HttpStatusCode.InternalServerError)
                }
            )

            assertEquals(
                BehandlingsstatusDto("Ukjent"),
                behandlingsstatusClient.hentBehandlingsstatus(LocalDate.now(), subjectToken)
            )
        }
    }

    @Test
    fun `Håndterer null som status`() {
        runBlocking {
            val behandlingsstatusClient = BehandlingsstatusHttpClient(
                baseUrl,
                innsynAudience,
                testTokenProvider,
                engine = MockEngine {
                    respond(
                        content = """{"behandlingsstatus":null}""".trimMargin(),
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            )

            assertEquals(
                BehandlingsstatusDto("Ukjent"),
                behandlingsstatusClient.hentBehandlingsstatus(LocalDate.now(), subjectToken)
            )
        }
    }
}
