package no.nav.dagpenger.soknad.arbeidsforhold

import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class ArbeidsforholdRouteTest {

    @Test
    fun `Skal avvise uautentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication() {
            assertEquals(HttpStatusCode.Unauthorized, client.get("${Configuration.basePath}/personalia").status)
        }
    }

    @Test
    fun `henter tom arbeidsforhold list`() {
        val mockAaregClient = mockk<AaregClient>().also {
            coEvery { it.hentArbeidsforhold(TestApplication.defaultDummyFodselsnummer, any()) } returns emptyList()
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                aaregClient = mockAaregClient,
            ),
        ) {
            autentisert(
                endepunkt = "${Configuration.basePath}/arbeidsforhold",
                httpMethod = HttpMethod.Get,
            ).let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.headers["Content-Type"])

                response.bodyAsText() shouldBe "[]"
                coVerify(exactly = 1) {
                    mockAaregClient.hentArbeidsforhold(
                        TestApplication.defaultDummyFodselsnummer,
                        any(),
                    )
                }
            }
        }
    }

    @Test
    fun `henter arbeidsforhold`() {
        val mockAaregClient = mockk<AaregClient>().also {
            coEvery { it.hentArbeidsforhold(TestApplication.defaultDummyFodselsnummer, any()) } returns arbeidsforhold
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                aaregClient = mockAaregClient,
            ),
        ) {
            autentisert(
                endepunkt = "${Configuration.basePath}/arbeidsforhold",
                httpMethod = HttpMethod.Get,
            ).let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.headers["Content-Type"])

                response.bodyAsText() shouldBeEqual forventetArbeidsforhold
                coVerify(exactly = 1) {
                    mockAaregClient.hentArbeidsforhold(
                        TestApplication.defaultDummyFodselsnummer,
                        any(),
                    )
                }
            }
        }
    }
}

private val forventetArbeidsforhold: String
    get() {
        return objectMapper.writeValueAsString(arbeidsforhold)
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
        startdato = LocalDate.of(2020, 1, 1),
        sluttdato = LocalDate.of(2021, 1, 1),
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
        startdato = LocalDate.of(2020, 1, 1),
        sluttdato = LocalDate.of(2021, 1, 1),
        endringsAarsak = "Permitert",
        sluttAarsak = "Permitert",
    ),
)
