package no.nav.dagpenger.quizshow.api

import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ApiTest {
    @Test
    fun `oppretter SSE connection`() {
        withTestApplication({
            søknadApi()
        }) {
            with(handleRequest(HttpMethod.Get, "/sse")) {
                assertEquals(HttpStatusCode.OK, response.status())
                val headers = response.headers
                assertEquals("text/event-stream; charset=UTF-8", headers.get("Content-Type"))
            }
        }
    }
}
