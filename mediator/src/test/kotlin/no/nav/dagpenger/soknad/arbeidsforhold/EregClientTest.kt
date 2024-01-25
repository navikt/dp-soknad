package no.nav.dagpenger.soknad.arbeidsforhold

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EregClientTest {

    @Test
    fun `henter organisasjonsnavn`() {
        val eregClient = EregClient(
            eregUrl = "http://example.com",
            engine = createMockedClient(200, """{"navn": {"navnelinje1": "ABC AS"}}"""),
        )

        eregClient.hentOganisasjonsnavn("123456789") shouldBe "ABC AS"
    }

    @Test
    fun `kan ikke hente organisasjonsnavn`() {
        val eregClient = EregClient(
            eregUrl = "http://example.com",
            engine = createMockedClient(404, ""),
        )

        eregClient.hentOganisasjonsnavn("123456789") shouldBe null
    }
}
