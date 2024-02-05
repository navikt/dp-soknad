package no.nav.dagpenger.soknad.arbeidsforhold

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ArbeidsforholdOppslagTest {

    private lateinit var arbeidsforholdOppslag: ArbeidsforholdOppslag
    private lateinit var aaregClient: AaregClient
    private lateinit var eregClient: EregClient
    private lateinit var fnr: String
    private lateinit var subjectToken: String

    private val orgnummer = "910825518"

    @BeforeEach
    fun setup() {
        aaregClient = mockk()
        eregClient = mockk()
        arbeidsforholdOppslag = ArbeidsforholdOppslag(
            aaregClient = aaregClient,
            eregClient = eregClient,
        )
        fnr = "12345678903"
        subjectToken = "gylidg_token"
    }

    @Test
    fun `oppslaget returnerer tom liste dersom vi får tom liste fra aareg`() {
        runBlocking {
            coEvery { aaregClient.hentArbeidsforhold(fnr, subjectToken) } returns emptyList()

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold(fnr, subjectToken)

            arbeidsforhold shouldBe emptyList()
            coVerify(exactly = 1) { aaregClient.hentArbeidsforhold(fnr, subjectToken) }
            coVerify(exactly = 0) { eregClient.hentOganisasjonsnavn(any()) }
        }
    }

    @Test
    fun `oppslaget returnerer en tom liste dersom vi får minst ett arbeidsforhold fra aareg uten orgnummer`() {
        runBlocking {
            val arbeidsforholdHvorEttManglerOrgnummer = listOf(
                Arbeidsforhold(
                    id = "H911050676R16054L0001",
                    organisasjonsnummer = null,
                    startdato = LocalDate.of(2014, 1, 1),
                    sluttdato = LocalDate.of(2015, 1, 1),
                    stillingsprosent = 100.0,
                ),
                Arbeidsforhold(
                    id = "V911050676R16054L0001",
                    organisasjonsnummer = orgnummer,
                    startdato = LocalDate.of(2016, 1, 1),
                    sluttdato = null,
                    stillingsprosent = 100.0,
                ),
            )
            coEvery { aaregClient.hentArbeidsforhold(fnr, subjectToken) } returns arbeidsforholdHvorEttManglerOrgnummer
            coEvery { eregClient.hentOganisasjonsnavn(any()) } returns null andThen "TEST AS"

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold(fnr, subjectToken)

            arbeidsforhold shouldBe emptyList()
            coVerify(exactly = 1) { aaregClient.hentArbeidsforhold(fnr, subjectToken) }
            coVerify(exactly = 2) { eregClient.hentOganisasjonsnavn(any()) }
        }
    }

    @Test
    fun `oppslaget returnerer en tom liste dersom minst et arbeidsforhold har ugyldig organisasjonsnummer`() {
        runBlocking {
            val arbeidsforholdMedUgyldigeOrgnr = listOf(
                Arbeidsforhold(
                    id = "H911050676R16054L0001",
                    organisasjonsnummer = "ugyldig_orgnummer",
                    startdato = LocalDate.of(2014, 1, 1),
                    sluttdato = LocalDate.of(2015, 1, 1),
                    stillingsprosent = 100.0,
                ),
                Arbeidsforhold(
                    id = "V911050676R16054L0001",
                    organisasjonsnummer = orgnummer,
                    startdato = LocalDate.of(2016, 1, 1),
                    sluttdato = null,
                    stillingsprosent = 100.0,
                ),
            )
            coEvery { aaregClient.hentArbeidsforhold(fnr, subjectToken) } returns arbeidsforholdMedUgyldigeOrgnr
            coEvery { eregClient.hentOganisasjonsnavn(any()) } returns null andThen "TEST AS"

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold(fnr, subjectToken)

            arbeidsforhold shouldBe emptyList()
            coVerify(exactly = 1) { aaregClient.hentArbeidsforhold(fnr, subjectToken) }
            coVerify(exactly = 1) { eregClient.hentOganisasjonsnavn("ugyldig_orgnummer") }
            coVerify(exactly = 1) { eregClient.hentOganisasjonsnavn(orgnummer) }
        }
    }

    @Test
    fun `oppslaget returnerer liste med arbeidsforhold uten orgnummer og med organisasjonsnavn`() {
        runBlocking {
            val arbeidsforholdMedGyldigeOrgnr = listOf(
                Arbeidsforhold(
                    id = "H911050676R16054L0001",
                    organisasjonsnummer = orgnummer,
                    startdato = LocalDate.of(2014, 1, 1),
                    sluttdato = LocalDate.of(2015, 1, 1),
                    stillingsprosent = 100.0,
                ),
                Arbeidsforhold(
                    id = "V911050676R16054L0001",
                    organisasjonsnummer = orgnummer,
                    startdato = LocalDate.of(2016, 1, 1),
                    sluttdato = null,
                    stillingsprosent = 100.0,
                ),
            )
            coEvery { aaregClient.hentArbeidsforhold(fnr, subjectToken) } returns arbeidsforholdMedGyldigeOrgnr
            coEvery { eregClient.hentOganisasjonsnavn(any()) } returns "ABC AS" andThen "DEF AS"

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold(fnr, subjectToken)

            with(arbeidsforhold) {
                size shouldBe 2
                with(get(0)) {
                    id shouldBe "H911050676R16054L0001"
                    organisasjonsnavn shouldBe "ABC AS"
                }
                with(get(1)) {
                    id shouldBe "V911050676R16054L0001"
                    organisasjonsnavn shouldBe "DEF AS"
                }
            }
            coVerify(exactly = 1) { aaregClient.hentArbeidsforhold(fnr, subjectToken) }
            coVerify(exactly = 2) { eregClient.hentOganisasjonsnavn(orgnummer) }
        }
    }
}
