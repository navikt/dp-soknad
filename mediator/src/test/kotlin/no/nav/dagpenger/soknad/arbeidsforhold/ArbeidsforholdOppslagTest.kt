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
                arbeidsforhold(orgnummer = null),
                arbeidsforhold(),
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
                arbeidsforhold(orgnummer = "ugyldig_orgnummer"),
                arbeidsforhold(),
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
    fun `oppslaget returnerer liste med arbeidsforhold mappet til respons uten orgnummer og med organisasjonsnavn`() {
        runBlocking {
            val kompletteArbeidsforhold = listOf(arbeidsforhold(), arbeidsforhold())
            coEvery { aaregClient.hentArbeidsforhold(fnr, subjectToken) } returns kompletteArbeidsforhold
            coEvery { eregClient.hentOganisasjonsnavn(any()) } returns "ABC AS" andThen "DEF AS"

            val arbeidsforhold = arbeidsforholdOppslag.hentArbeidsforhold(fnr, subjectToken)

            arbeidsforhold.size shouldBe 2
            arbeidsforhold[0].organisasjonsnavn shouldBe "ABC AS"
            arbeidsforhold[1].organisasjonsnavn shouldBe "DEF AS"

            coVerify(exactly = 1) { aaregClient.hentArbeidsforhold(fnr, subjectToken) }
            coVerify(exactly = 2) { eregClient.hentOganisasjonsnavn(orgnummer) }
        }
    }

    private fun arbeidsforhold(orgnummer: String? = this.orgnummer) = Arbeidsforhold(
        id = "H911050676R16054L0001",
        organisasjonsnummer = orgnummer,
        startdato = LocalDate.of(2014, 1, 1),
        sluttdato = LocalDate.of(2015, 1, 1),
    )
}
