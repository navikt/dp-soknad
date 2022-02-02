package no.nav.dagpenger.quizshow.api.personalia

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.natpryce.konfig.Configuration
import io.ktor.client.HttpClient
import io.ktor.client.features.DefaultRequest
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import no.nav.dagpenger.quizshow.api.Configuration.dpProxyScope
import no.nav.dagpenger.quizshow.api.Configuration.dpProxyTokenProvider
import no.nav.dagpenger.quizshow.api.Configuration.dpProxyUrl

internal class KontonummerOppslag(private val config: Configuration) {

    private val tokenProvider = config.dpProxyTokenProvider
    private val dpProxyScope = config.dpProxyScope

    private val dpProxyClient = HttpClient() {

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

        return dpProxyClient.request("${config.dpProxyUrl}/proxy/v1/kontonummer") {
            method = HttpMethod.Post
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.clientCredentials(dpProxyScope).accessToken}")
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Accept, "application/json")
            body = mapOf("fnr" to fnr)
        }
    }
}

data class Kontonummer(val kontonummer: String, val banknavn: String?, val landkode: String?)
