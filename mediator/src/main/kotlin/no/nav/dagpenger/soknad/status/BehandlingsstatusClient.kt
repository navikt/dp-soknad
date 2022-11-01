package no.nav.dagpenger.soknad.status

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Configuration.tokenXClient
import java.time.LocalDate

internal interface BehandlingsstatusClient {
    suspend fun hentBehandlingsstatus(fom: LocalDate, subjectToken: String): BehandlingsstatusDto
}

internal class BehandlingsstatusHttpClient(
    private val baseUrl: String = Configuration.dpInnsynUrl,
    private val innsynAudience: String = Configuration.dpInnsynAudience,
    private val tokenProvider: (token: String, audience: String) -> String = exchangeToOboToken,
    engine: HttpClientEngine = CIO.create()
) : BehandlingsstatusClient {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val httpClient = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    override suspend fun hentBehandlingsstatus(fom: LocalDate, subjectToken: String): BehandlingsstatusDto {
        val url = "$baseUrl/behandlingsstatus?fom=$fom"
        return try {
            httpClient.get(url) {
                addBearerToken(subjectToken)
                contentType(ContentType.Application.Json)
            }.body()
        } catch (e: Exception) {
            logger.error { "Feil under henting av behandlingsstatus. Behandlingsstatus settes til ukjent: ${e.message}" }
            return BehandlingsstatusDto("Ukjent")
        }
    }

    private fun HttpRequestBuilder.addBearerToken(subjectToken: String) {
        headers[HttpHeaders.Authorization] = "Bearer ${tokenProvider.invoke(subjectToken, innsynAudience)}"
    }
}

internal data class BehandlingsstatusDto(
    @JsonProperty("behandlingsstatus")
    val behandlingsstatus: String
)

private val exchangeToOboToken = { token: String, audience: String ->
    tokenXClient.tokenExchange(token, audience).accessToken
}
