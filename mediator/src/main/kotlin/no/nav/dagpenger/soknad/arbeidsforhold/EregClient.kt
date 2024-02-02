package no.nav.dagpenger.soknad.arbeidsforhold

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.appendEncodedPathSegments
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.utils.client.createHttpClient

internal class EregClient(
    private val eregUrl: String = Configuration.eregUrl,
    engine: HttpClientEngine = CIO.create {},
) {

    private val client = createHttpClient(engine)

    fun hentOganisasjonsnavn(organisasjonsnummer: String?): String? = runBlocking {
        if (organisasjonsnummer === null || organisasjonsnummer.length != 9) {
            logger.warn("Organisasjonsnummer må være 9 siffer")
            return@runBlocking null
        }

        val url = URLBuilder(eregUrl)
            .appendEncodedPathSegments(
                EREG_NOEKKELINFO_PATH.replace("{orgnummer}", organisasjonsnummer),
            )
            .build()

        try {
            val response = client.get(url)

            if (response.status.value == 200) {
                logger.info("Kall til EREG gikk OK")
                val jsonResponse = jacksonObjectMapper().readTree(response.bodyAsText())
                jsonResponse["navn"]["navnelinje1"].asText()
            } else {
                logger.warn("Kall til EREG feilet med status ${response.status}")
                null
            }
        } catch (e: Exception) {
            logger.warn("Kall til EREG feilet", e)
            null
        }
    }

    companion object {
        private const val EREG_NOEKKELINFO_PATH = "v2/organisasjon/{orgnummer}/noekkelinfo"
        private val logger = KotlinLogging.logger {}
    }
}
