package no.nav.dagpenger.soknad.arbeidsforhold

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EregClientTest {
    @Test
    fun `kan hente organisasjonsnavn ved gyldig orgnummer`() {
        val eregClient =
            EregClient(
                eregUrl = "http://example.com",
                engine = createMockedClient(200, """{"navn": {"navnelinje1": "ABC AS"}}"""),
            )

        eregClient.hentOganisasjonsnavn("123456789") shouldBe "ABC AS"
    }

    @Test
    fun `returnerer null dersom gitt organisasjonsnummer er ugyldig`() {
        val eregClient =
            EregClient(
                eregUrl = "http://example.com",
                engine = createMockedClient(200, ""),
            )

        eregClient.hentOganisasjonsnavn("ugyldig_orgnummer") shouldBe null
    }

    @Test
    fun `håndterer mulige feilkoder fra EREG ved å returnere null`() {
        val feilkoderFraEREG = listOf(400, 401, 500)

        feilkoderFraEREG.forEach { feilkode ->
            val eregClient =
                EregClient(
                    eregUrl = "http://example.com",
                    engine = createMockedClient(feilkode, ""),
                )

            eregClient.hentOganisasjonsnavn("123456789") shouldBe null
        }
    }
}
