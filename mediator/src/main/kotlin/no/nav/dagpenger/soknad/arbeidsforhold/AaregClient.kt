package no.nav.dagpenger.soknad.arbeidsforhold

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendEncodedPathSegments
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.utils.client.createHttpClient
import no.nav.helse.rapids_rivers.asLocalDate

internal class AaregClient(
    private val aaregUrl: String = Configuration.aaregUrl,
    private val eregClient: EregClient,
    private val tokenProvider: (String) -> String,
    engine: HttpClientEngine = CIO.create {},
) {

    private val client = createHttpClient(engine)

    fun hentArbeidsforhold(
        fnr: String,
        subjectToken: String,
    ) = runBlocking {
        val url = URLBuilder(aaregUrl).appendEncodedPathSegments(API_PATH, ARBEIDSFORHOLD_PATH).build()
        try {
            val response: HttpResponse =
                client.get(url) {
                    header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke(subjectToken)}")
                    header("Nav-Personident", fnr)
                    parameter("arbeidsforholdstatus", "AKTIV, AVSLUTTET")
                    parameter("historikk", "true")
                }
            if (response.status.value == 200) {
                logger.info("Kall til AAREG gikk OK")
                val arbeidsforholdJson = jacksonObjectMapper().readTree(response.bodyAsText())

                arbeidsforholdJson.map {
                    toArbeidsforhold(it, null)
                }
            } else {
                logger.warn("Kall til AAREG feilet med status ${response.status}")
                emptyList()
            }
        } catch (e: ClientRequestException) {
            logger.warn("Kall til AAREG feilet", e)
            emptyList()
        }
    }

    companion object {
        private const val API_PATH = "api"
        private const val ARBEIDSFORHOLD_PATH = "v2/arbeidstaker/arbeidsforhold"
        private val logger = KotlinLogging.logger {}
    }
}

private fun toArbeidsforhold(aaregArbeidsforhold: JsonNode, organisasjonsnavn: String?): Arbeidsforhold {
    return Arbeidsforhold(
        id = aaregArbeidsforhold["id"].asText(),
        organisasjonsnummer = toOrganisasjonsnummer(aaregArbeidsforhold["arbeidssted"]) ?: "",
        organisasjonsnavn = organisasjonsnavn,
        startdato = aaregArbeidsforhold["ansettelsesperiode"]["startdato"].asLocalDate(),
        sluttdato = aaregArbeidsforhold["ansettelsesperiode"].get("sluttdato")?.asLocalDate(),
    )
}

private fun toOrganisasjonsnummer(arbeidssted: JsonNode): String? {
    return arbeidssted.get("identer")
        .firstOrNull { it["type"].asText() == "ORGANISASJONSNUMMER" }
        ?.get("ident")?.asText()
}
