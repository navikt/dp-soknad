package no.nav.dagpenger.soknad.status

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Configuration.tokenXClient
import org.slf4j.MDC
import java.time.LocalDate

internal interface BehandlingsstatusClient {
    suspend fun hentBehandlingsstatus(
        fom: LocalDate,
        subjectToken: String,
    ): BehandlingsstatusDto
}

internal class BehandlingsstatusHttpClient(
    private val baseUrl: String = Configuration.dpInnsynUrl,
    private val innsynAudience: String = Configuration.dpInnsynAudience,
    private val tokenProvider: (token: String, audience: String) -> String = exchangeToOboToken,
    engine: HttpClientEngine = CIO.create(),
) : BehandlingsstatusClient {
    private companion object {
        val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall.BehandlingsstatusClient")
    }

    private val httpClient =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                jackson {
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    registerModule(
                        KotlinModule.Builder()
                            .configure(KotlinFeature.NullIsSameAsDefault, enabled = true)
                            .build(),
                    )
                }
            }
        }

    override suspend fun hentBehandlingsstatus(
        fom: LocalDate,
        subjectToken: String,
    ): BehandlingsstatusDto {
        var rawBody: String? = null
        val url = "$baseUrl/behandlingsstatus?fom=$fom"
        logger.info { "Henter behandlingsstatus med fom=$fom" }
        return try {
            httpClient.get(url) {
                header(HttpHeaders.XRequestId, MDC.get("call-id"))
                addBearerToken(subjectToken)
                contentType(ContentType.Application.Json)
            }.run {
                rawBody = this.bodyAsText()
                this.body()
            }
        } catch (e: Exception) {
            logger.error(e) { "Feil under henting av behandlingsstatus. Behandlingsstatus settes til ukjent" }
            sikkerlogg.error(e) { "Feil under henting av behandlingsstatus. Behandlingsstatus settes til ukjent. Raw=$rawBody" }
            return BehandlingsstatusDto("Ukjent")
        }
    }

    private fun HttpRequestBuilder.addBearerToken(subjectToken: String) {
        headers[HttpHeaders.Authorization] = "Bearer ${tokenProvider.invoke(subjectToken, innsynAudience)}"
    }
}

internal data class BehandlingsstatusDto(
    val behandlingsstatus: String = "Ukjent",
)

private val exchangeToOboToken = { token: String, audience: String ->
    tokenXClient.tokenExchange(token, audience).access_token ?: throw RuntimeException("Fant ikke token")
}
