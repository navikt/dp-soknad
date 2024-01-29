package no.nav.dagpenger.soknad.arbeidsforhold

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal fun createMockedClient(statusCode: Int, responseBody: String): HttpClientEngine {
    val mockEngine =
        MockEngine {
            respond(
                content = responseBody,
                status = HttpStatusCode.fromValue(statusCode),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    return mockEngine
}
