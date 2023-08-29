package no.nav.dagpenger.soknad.personalia

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal class KontonummerOppslag(
    private val kontoRegisterUrl: String,
    private val tokenProvider: (subjektToken: String) -> String,
    httpClientEngine: HttpClientEngine = CIO.create(),
) {

    private val kontoNummberClient = HttpClient(httpClientEngine) {
        expectSuccess = true
        install(Logging) {
            level = LogLevel.INFO
        }
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
    }

    suspend fun hentKontonummer(subjektToken: String): Kontonummer {
        return try {
            kontoNummberClient.get(kontoRegisterUrl) {
                header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke(subjektToken)}")
                header(HttpHeaders.Accept, "application/json")
            }.body<KontoNummerRespsonse>()
                .let { map(it) }
        } catch (e: ClientRequestException) {
            if (e.response.status != HttpStatusCode.NotFound) {
                logger.error(e) { "Kunne ikke hente kontonummer" }
            }
            Kontonummer.TOM
        }
    }

    private fun map(response: KontoNummerRespsonse): Kontonummer {
        return if (!response.kontonummer.isNullOrBlank()) {
            Kontonummer(kontonummer = response.kontonummer)
        } else if (response.utenlandskKontoInfo != null) {
            Kontonummer(
                kontonummer = response.utenlandskKontoInfo.bankkode,
                banknavn = response.utenlandskKontoInfo.banknavn,
                bankLandkode = response.utenlandskKontoInfo.bankLandkode,
            )
        } else {
            Kontonummer.TOM
        }
    }

    private data class KontoNummerRespsonse(
        val kontonummer: String? = null,
        val utenlandskKontoInfo: UtenlandskKontoInfoResponse? = null,
    ) {
        data class UtenlandskKontoInfoResponse(
            val banknavn: String? = null,
            val bankkode: String? = null,
            val bankLandkode: String? = null,
        )
    }
}
