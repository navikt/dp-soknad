package no.nav.dagpenger.soknad.personalia

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.DefaultRequest
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
        install(JsonFeature) {
            serializer = JacksonSerializer(
                jackson = jacksonMapperBuilder()
                    .addModule(JavaTimeModule())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build()
            )
        }
    }

    suspend fun hentKontonummer(fnr: String): Kontonummer {
        return runCatching {
            dpProxyClient.request<Kontonummer>("$dpProxyUrl/proxy/v1/kontonummer") {
                method = HttpMethod.Post
                header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke()}")
                header(HttpHeaders.ContentType, "application/json")
                header(HttpHeaders.Accept, "application/json")
                body = mapOf("fnr" to fnr)
            }
        }.getOrElse { t ->
            logger.error(t) { "Fikk ikke hentet konto nummer" }
            Kontonummer()
        }
    }
}
