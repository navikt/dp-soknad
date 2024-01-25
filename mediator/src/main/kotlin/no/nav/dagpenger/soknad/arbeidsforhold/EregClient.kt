package no.nav.dagpenger.soknad.arbeidsforhold

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Configuration

internal class EregClient(
    private val eregUrl: String = Configuration.eregUrl,
    engine: HttpClientEngine = CIO.create {},
) {

    private val httpClient = HttpClient(engine) {
        expectSuccess = true

        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    fun hentOganisasjonsnavn(orgnummer: String): String? = runBlocking {
        val url = "$eregUrl$EREG_NOEKKELINFO_PATH$"

        try {
            val response =
                httpClient.get(url) {
                    parameter("orgnummer", orgnummer)
                }

            if (response.status.value == 200) {
                logger.info("Kall til EREG gikk OK")
                val jsonResponse = jacksonObjectMapper().readTree(response.bodyAsText())
                jsonResponse["navn"]["navnelinje1"].asText()
            } else {
                logger.warn("Kall til EREG feilet med status ${response.status}")
                null
            }
        } catch (e: ClientRequestException) {
            logger.warn("Kall til EREG feilet", e)
            null
        }
    }

    companion object {
        private const val EREG_NOEKKELINFO_PATH = "v2/organisasjon/{orgnummer}/noekkelinfo"
        private val logger = KotlinLogging.logger {}
    }
}
