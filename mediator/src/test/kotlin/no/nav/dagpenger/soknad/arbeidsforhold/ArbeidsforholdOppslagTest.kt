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

    @BeforeEach
    fun setup() {
        aaregClient = mockk<AaregClient>()
        eregClient = mockk<EregClient>()
        arbeidsforholdOppslag = ArbeidsforholdOppslag(
            aaregClient = aaregClient,
            eregClient = eregClient,
        )
        fnr = "12345678903"
        subjectToken = "gylidg_token"
    }

    @Test
    fun `tom liste av arbeidsforhold`() {
        runBlocking {
            coEvery { aaregClient.hentArbeidsforhold(fnr, subjectToken) } returns emptyList()

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold(fnr, subjectToken)

            arbeidsforhold shouldBe emptyList()
            coVerify(exactly = 1) { aaregClient.hentArbeidsforhold(fnr, subjectToken) }
            coVerify(exactly = 0) { eregClient.hentOganisasjonsnavn(any()) }
        }
    }

    @Test
    fun `liste over arbeidsforhold som ikke har orginasjonsnummer`() {
        runBlocking {
            val arbeidsforholdUtenOrgnr = listOf(
                Arbeidsforhold(
                    id = "H911050676R16054L0001",
                    organisasjonsnnummer = null,
                    startdato = LocalDate.of(2014, 1, 1),
                    sluttdato = LocalDate.of(2015, 1, 1),
                ),
                Arbeidsforhold(
                    id = "V911050676R16054L0001",
                    organisasjonsnnummer = null,
                    startdato = LocalDate.of(2016, 1, 1),
                    sluttdato = null,
                ),
            )
            coEvery { aaregClient.hentArbeidsforhold(fnr, subjectToken) } returns arbeidsforholdUtenOrgnr
            coEvery { eregClient.hentOganisasjonsnavn(any()) } returns null

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold(fnr, subjectToken)

            arbeidsforhold shouldBe emptyList()
            coVerify(exactly = 1) { aaregClient.hentArbeidsforhold(fnr, subjectToken) }
            coVerify(exactly = 2) { eregClient.hentOganisasjonsnavn(null) }
        }
    }

    @Test
    fun `liste over arbeidsforhold med ugyldige organisasjonsnummer`() {
        runBlocking {
            val arbeidsforholdMedUgyldigeOrgnr = listOf(
                Arbeidsforhold(
                    id = "H911050676R16054L0001",
                    organisasjonsnnummer = "12345678",
                    startdato = LocalDate.of(2014, 1, 1),
                    sluttdato = LocalDate.of(2015, 1, 1),
                ),
                Arbeidsforhold(
                    id = "V911050676R16054L0001",
                    organisasjonsnnummer = "1234567890",
                    startdato = LocalDate.of(2016, 1, 1),
                    sluttdato = null,
                ),
            )
            coEvery { aaregClient.hentArbeidsforhold(fnr, subjectToken) } returns arbeidsforholdMedUgyldigeOrgnr
            coEvery { eregClient.hentOganisasjonsnavn(any()) } returns null

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold(fnr, subjectToken)

            arbeidsforhold shouldBe emptyList()
            coVerify(exactly = 1) { aaregClient.hentArbeidsforhold(fnr, subjectToken) }
            coVerify(exactly = 1) { eregClient.hentOganisasjonsnavn("12345678") }
            coVerify(exactly = 1) { eregClient.hentOganisasjonsnavn("1234567890") }
        }
    }

    @Test
    fun `liste over arbeidsforhold med delvis gyldige organisasjonsnummer`() {
        runBlocking {
            val arbeidsforholdMedDelvisGyldigeOrgnr = listOf(
                Arbeidsforhold(
                    id = "H911050676R16054L0001",
                    organisasjonsnnummer = "910825518",
                    startdato = LocalDate.of(2014, 1, 1),
                    sluttdato = LocalDate.of(2015, 1, 1),
                ),
                Arbeidsforhold(
                    id = "V911050676R16054L0001",
                    organisasjonsnnummer = null,
                    startdato = LocalDate.of(2016, 1, 1),
                    sluttdato = null,
                ),
            )
            coEvery { aaregClient.hentArbeidsforhold(fnr, subjectToken) } returns arbeidsforholdMedDelvisGyldigeOrgnr
            coEvery { eregClient.hentOganisasjonsnavn(any()) } returns "ABC AS" andThen null

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold(fnr, subjectToken)

            with(arbeidsforhold) {
                size shouldBe 1
                with(get(0)) {
                    id shouldBe "H911050676R16054L0001"
                    organisasjonsnavn shouldBe "ABC AS"
                }
            }
            coVerify(exactly = 1) { aaregClient.hentArbeidsforhold(fnr, subjectToken) }
            coVerify(exactly = 1) { eregClient.hentOganisasjonsnavn("910825518") }
            coVerify(exactly = 1) { eregClient.hentOganisasjonsnavn(null) }
        }
    }

    @Test
    fun `liste over arbeidsforhold med gyldige organisasjonsnummer`() {
        runBlocking {
            val arbeidsforholdMedGyldigeOrgnr = listOf(
                Arbeidsforhold(
                    id = "H911050676R16054L0001",
                    organisasjonsnnummer = "910825518",
                    startdato = LocalDate.of(2014, 1, 1),
                    sluttdato = LocalDate.of(2015, 1, 1),
                ),
                Arbeidsforhold(
                    id = "V911050676R16054L0001",
                    organisasjonsnnummer = "910825577",
                    startdato = LocalDate.of(2016, 1, 1),
                    sluttdato = null,
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
            coVerify(exactly = 1) { eregClient.hentOganisasjonsnavn("910825518") }
            coVerify(exactly = 1) { eregClient.hentOganisasjonsnavn("910825577") }
        }
    }
}
