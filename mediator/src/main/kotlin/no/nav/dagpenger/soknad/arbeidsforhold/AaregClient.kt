package no.nav.dagpenger.soknad.arbeidsforhold

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendEncodedPathSegments
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Configuration
import java.time.LocalDate

internal class AaregClient(
    private val aaregUrl: String = Configuration.aaregUrl,
    private val tokenProvider: (String) -> String,
    engine: HttpClientEngine = CIO.create {},
) {

    private val httpClient =
        HttpClient(engine) {
            expectSuccess = true

            install(ContentNegotiation) {
                jackson {
                    registerModule(JavaTimeModule())
                    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
        }

    fun hentArbeidsforhold(
        fnr: String,
        subjectToken: String,
    ) = runBlocking {
        val url = URLBuilder(aaregUrl).appendEncodedPathSegments(API_PATH, ARBEIDSFORHOLD_PATH).build()
        try {
            val response: HttpResponse =
                httpClient.get(url) {
                    header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke(subjectToken)}")
                    header("Nav-Personident", fnr)
                    parameter("arbeidsforholdstatus", "AKTIV, AVSLUTTET")
                    parameter("historikk", "true")
                }
            if (response.status.value == 200) {
                logger.info("Kall til AAREG gikk OK")
                arbeidsforhold
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

private val arbeidsforhold: List<Arbeidsforhold> = listOf(
    Arbeidsforhold(
        id = "1",
        arbeidsgiver = Arbeidsgiver(
            navn = "ABC",
            land = "NORGE",
        ),
        ansettelsesdetaljer = Ansettelsesdetaljer(
            stillingsprosent = 60.00,
            antallTimerPerUke = 22.5,
            ansettelsesform = "fast",
        ),
        periode = Periode(
            startdato = LocalDate.of(2020, 1, 1),
            sluttdato = LocalDate.of(2021, 1, 1),
        ),
        endringsAarsak = "AVSKJEDIGET",
        sluttAarsak = "AVSKJEDIGET",
    ),
    Arbeidsforhold(
        id = "2",
        arbeidsgiver = Arbeidsgiver(
            navn = "DEF",
            land = "NORGE",
        ),
        ansettelsesdetaljer = Ansettelsesdetaljer(
            stillingsprosent = 100.00,
            antallTimerPerUke = 37.5,
            ansettelsesform = "fast",
        ),
        periode = Periode(
            startdato = LocalDate.of(2020, 1, 1),
            sluttdato = LocalDate.of(2021, 1, 1),
        ),
        endringsAarsak = "Permitert",
        sluttAarsak = "Permitert",
    ),

)
