package no.nav.dagpenger.soknad.personalia

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal class KontonummerOppslag(
    private val dpProxyUrl: String,
    private val tokenProvider: () -> String,
    httpClientEngine: HttpClientEngine = CIO.create()
) {

    private val dpProxyClient = HttpClient(httpClientEngine) {

        install(DefaultRequest) {
        }
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
    }

    suspend fun hentKontonummer(fnr: String): Kontonummer {
        return runCatching {
            dpProxyClient.post("$dpProxyUrl/proxy/v1/kontonummer") {
                header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Accept, "application/json")
                setBody(mapOf("fnr" to fnr))
            }.body<Kontonummer>()
        }.getOrElse { t ->
            logger.error(t) { "Fikk ikke hentet konto nummer" }
            Kontonummer()
        }
    }
}
