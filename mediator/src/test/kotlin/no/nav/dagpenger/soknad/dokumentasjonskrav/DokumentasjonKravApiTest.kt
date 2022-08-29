package no.nav.dagpenger.soknad.dokumentasjonskrav

import de.slub.urn.URN
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import no.nav.dagpenger.soknad.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.soknad.TestApplication.mockedSøknadApi
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.hendelse.KravHendelse
import no.nav.dagpenger.soknad.livssyklus.asUUID
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class DokumentasjonKravApiTest {

    private val testSoknadId = UUID.fromString("d172a832-4f52-4e1f-ab5f-8be8348d9280")

    private val dokumentFaktum = Faktum(faktumJson(id = "1", beskrivendeId = "f1"))
    private val faktaSomSannsynliggjøres = mutableSetOf(Faktum(faktumJson(id = "2", beskrivendeId = "f2")))
    private val sannsynliggjøring = Sannsynliggjøring(
        id = dokumentFaktum.id,
        faktum = dokumentFaktum,
        sannsynliggjør = faktaSomSannsynliggjøres
    )

    private val dokumentKrav = Dokumentkrav().also {
        it.håndter(setOf(sannsynliggjøring))
    }

    private val søknadMediatorMock = mockk<SøknadMediator>().also {
        every { it.hentDokumentkravFor(testSoknadId, defaultDummyFodselsnummer) } returns dokumentKrav

    }

    @Test
    fun `Skal avvise uautentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication() {
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("${Configuration.basePath}/soknad/id/dokumentasjonskrav").status
            )
        }
    }

    @Test
    fun `skal vise dokumentasjons krav`() {
        TestApplication.withMockAuthServerAndTestApplication(
            mockedSøknadApi(
                søknadMediator = søknadMediatorMock
            )
        ) {
            autentisert(
                httpMethod = Get,
                endepunkt = "${Configuration.basePath}/soknad/$testSoknadId/dokumentasjonskrav"
            ).let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())
                val dokumentkrav = response.bodyAsText().let { objectMapper.readTree(it) }
                assertNotNull(dokumentkrav)
                assertEquals(testSoknadId, dokumentkrav["soknad_uuid"].asUUID())
                assertFalse(dokumentkrav["krav"].isNull)
                with(dokumentkrav["krav"].first()) {
                    assertNotNull(this["id"])
                    assertNotNull(this["beskrivendeId"])
                    assertNotNull(this["filer"])
                    assertNotNull(this["gyldigeValg"])
                    assertNotNull(this["begrunnelse"])
                    assertNotNull(this["svar"])
                    assertNotNull(this["fakta"])
                }
            }
        }
    }

    @Test
    fun `Skal kunne besvare`() {

        val slot = slot<KravHendelse>()
        val tidspunkt = LocalDateTime.now()
        val mediatorMock = mockk<SøknadMediator>().also {
            every { it.behandle(capture(slot)) } just Runs
        }
        TestApplication.withMockAuthServerAndTestApplication(
            mockedSøknadApi(
                søknadMediator = mediatorMock
            )
        ) {
            client.put("${Configuration.basePath}/soknad/$testSoknadId/dokumentasjonskrav/451/fil") {
                autentisert()
                header(HttpHeaders.ContentType, "application/json")
                // language=JSON

                setBody(
                    """{
  "filnavn": "ja.jpg",
  "storrelse": 50000,
  "urn": "urn:vedlegg:1111/123234",
  "tidspunkt": "$tidspunkt"
}"""
                )
            }.let { response ->
                assertEquals(HttpStatusCode.Created, response.status)
                assertTrue(slot.isCaptured)
                with(slot.captured) {

                    assertEquals("451", this.kravId)
                    assertEquals(
                        Krav.Fil(
                            filnavn = "ja.jpg",
                            urn = URN.rfc8141().parse("urn:vedlegg:1111/123234"),
                            storrelse = 50000,
                            tidspunkt = tidspunkt,

                            ), this.fil
                    )
                }
            }
        }
    }
}
