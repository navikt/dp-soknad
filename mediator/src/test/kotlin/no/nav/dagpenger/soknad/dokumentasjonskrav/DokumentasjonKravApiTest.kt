package no.nav.dagpenger.soknad.dokumentasjonskrav

import de.slub.urn.URN
import io.ktor.client.request.delete
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
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Krav.Svar.SvarValg.SENDER_IKKE
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import no.nav.dagpenger.soknad.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.soknad.TestApplication.mockedSøknadApi
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.livssyklus.asUUID
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class DokumentasjonKravApiTest {
    private val testSoknadId = UUID.fromString("d172a832-4f52-4e1f-ab5f-8be8348d9280")
    private val dokumentFaktum1 = Faktum(faktumJson(id = "1", beskrivendeId = "f1", generertAv = "foobar"))
    private val dokumentFaktum2 = Faktum(faktumJson(id = "2", beskrivendeId = "f2"))
    private val faktaSomSannsynliggjøres = mutableSetOf(Faktum(faktumJson(id = "2", beskrivendeId = "f2")))
    private val sannsynliggjøring1 = Sannsynliggjøring(
        id = dokumentFaktum1.id,
        faktum = dokumentFaktum1,
        sannsynliggjør = faktaSomSannsynliggjøres
    )
    private val sannsynliggjøring2 = Sannsynliggjøring(
        id = dokumentFaktum2.id,
        faktum = dokumentFaktum2,
        sannsynliggjør = faktaSomSannsynliggjøres
    )
    private val fil = Krav.Fil(
        "test.jpg",
        URN.rfc8141().parse("urn:nav:1"),
        89900,
        ZonedDateTime.now(),
        bundlet = false,
    )
    private val dokumentKrav = Dokumentkrav().also {
        it.håndter(setOf(sannsynliggjøring1, sannsynliggjøring2))
        it.håndter(LeggTilFil(testSoknadId, defaultDummyFodselsnummer, "1", fil))
        it.håndter(
            DokumentKravSammenstilling(
                testSoknadId,
                defaultDummyFodselsnummer,
                "1",
                URN.rfc8141().parse("urn:bundle:1")
            )
        )
        it.håndter(
            DokumentasjonIkkeTilgjengelig(
                testSoknadId,
                defaultDummyFodselsnummer,
                "2",
                valg = SENDER_IKKE,
                begrunnelse = "Har ikke dokumentasjon tilgjengelig"
            )
        )
    }
    private val søknad = Søknad.rehydrer(
        søknadId = testSoknadId,
        ident = defaultDummyFodselsnummer,
        ZonedDateTime.now(),
        språk = Språk("NO"),
        dokumentkrav = dokumentKrav,
        sistEndretAvBruker = ZonedDateTime.now(),
        tilstandsType = Søknad.Tilstand.Type.Påbegynt,
        aktivitetslogg = Aktivitetslogg(),
        null,
        null
    )
    private val søknadMediatorMock = mockk<SøknadMediator>().also {
        every { it.hent(testSoknadId) } returns søknad
        every { it.hentEier(testSoknadId) } returns defaultDummyFodselsnummer
    }

    @Test
    fun `Skal avvise uautentiserte kall`() {
        val søknadId = UUID.randomUUID()
        TestApplication.withMockAuthServerAndTestApplication {
            assertEquals(
                HttpStatusCode.Unauthorized,
                client.get("${Configuration.basePath}/soknad/id/dokumentasjonskrav").status
            )

            assertEquals(
                HttpStatusCode.Unauthorized,
                autentisert(
                    "${Configuration.basePath}/soknad/$søknadId/dokumentasjonskrav",
                    token = "hubba"
                ).status
            )
        }
    }

    @Test
    fun `Skal avvise autentiserte kall der pid på token ikke er eier av søknaden`() {
        val søknadId = UUID.randomUUID()
        val mediatorMock = mockk<SøknadMediator>().also {
            every { it.hentEier(søknadId) } returns "hubba"
        }

        TestApplication.withMockAuthServerAndTestApplication(
            mockedSøknadApi(søknadMediator = mediatorMock)
        ) {
            assertEquals(
                HttpStatusCode.Forbidden,
                autentisert("${Configuration.basePath}/soknad/$søknadId/dokumentasjonskrav").status
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
                endepunkt = "${Configuration.basePath}/soknad/$testSoknadId/dokumentasjonskrav",
                token = TestApplication.azureAdToken
            ).let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
            }

            autentisert(
                httpMethod = Get,
                endepunkt = "${Configuration.basePath}/soknad/$testSoknadId/dokumentasjonskrav",
                token = TestApplication.testTokenXToken
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
                    with(this["filer"].first()) {
                        assertEquals(fil.filnavn, this["filnavn"].asText())
                        assertEquals(fil.urn.toString(), this["urn"].asText())
                        assertEquals(fil.storrelse, this["storrelse"].asLong())
                        assertEquals(
                            fil.tidspunkt.toOffsetDateTime(),
                            ZonedDateTime.parse(this["tidspunkt"].asText()).toOffsetDateTime()
                        )
                        assertEquals(fil.bundlet, this["bundlet"].asBoolean())
                    }
                    assertNotNull(this["gyldigeValg"])
                    assertEquals("foobar", this["beskrivelse"].asText())
                    assertNull(this["begrunnelse"])
                    val svar = this["svar"]
                    assertFalse(svar.isNull)
                    assertEquals("dokumentkrav.svar.send.naa", svar.asText())
                    val bundle = this["bundle"]
                    assertFalse(bundle.isNull)
                    assertEquals("urn:bundle:1", bundle.asText())
                    val bundleFilsti = this["bundleFilsti"]
                    assertFalse(bundleFilsti.isNull)
                    assertEquals("1", bundleFilsti.asText())

                    assertNotNull(this["fakta"])
                }
                with(dokumentkrav["krav"].last()) {
                    assertNotNull(this["id"])
                    assertNotNull(this["beskrivendeId"])
                    assertNotNull(this["filer"])
                    assertNotNull(this["gyldigeValg"])
                    val svar = this["svar"]
                    assertFalse(svar.isNull)
                    assertEquals("dokumentkrav.svar.sender.ikke", svar.asText())
                    val begrunnelse = this["begrunnelse"]
                    assertFalse(begrunnelse.isNull)
                    assertEquals("Har ikke dokumentasjon tilgjengelig", begrunnelse.asText())
                }
            }
        }
    }

    @Test
    fun `Skal kunne sende inn svar uten fil`() {
        val slot = slot<DokumentasjonIkkeTilgjengelig>()
        val mediatorMock = mockk<SøknadMediator>().also {
            every { it.behandle(capture(slot)) } just Runs
            every { it.hentEier(testSoknadId) } returns defaultDummyFodselsnummer
        }

        TestApplication.withMockAuthServerAndTestApplication(
            mockedSøknadApi(
                søknadMediator = mediatorMock
            )
        ) {
            client.put("${Configuration.basePath}/soknad/$testSoknadId/dokumentasjonskrav/451/svar") {
                autentisert()
                header(HttpHeaders.ContentType, "application/json")
                // language=JSON
                setBody(
                    """
                    {
                      "begrunnelse": "har ikke",
                      "svar": "dokumentkrav.svar.send.senere"
                    }
                    """.trimIndent()
                )
            }.let { response ->
                assertEquals(HttpStatusCode.Created, response.status)
                assertTrue(slot.isCaptured)
                with(slot.captured) {
                    assertEquals("451", this.kravId)
                    assertEquals(Krav.Svar.SvarValg.SEND_SENERE, this.valg)
                    assertEquals("har ikke", this.begrunnelse)
                }
            }
        }
    }

    @Test
    fun `Skal kunne slette en fil`() {
        val slot = slot<SlettFil>()
        val mediatorMock = mockk<SøknadMediator>().also {
            every { it.behandle(capture(slot)) } just Runs
        }
        TestApplication.withMockAuthServerAndTestApplication(
            mockedSøknadApi(
                søknadMediator = mediatorMock
            )
        ) {
            client.delete("${Configuration.basePath}/soknad/$testSoknadId/dokumentasjonskrav/451/fil/soknadid/filid") {
                autentisert()
            }.let { response ->
                assertEquals(HttpStatusCode.NoContent, response.status)
                with(slot.captured) {
                    assertEquals("451", this.kravId)
                    assertEquals(testSoknadId, this.søknadID())
                    assertEquals(defaultDummyFodselsnummer, this.ident())
                    assertEquals("urn:vedlegg:soknadid/filid", this.urn.toString())
                }
            }
        }
    }

    @Test
    fun `Skal kunne besvare med fil`() {
        val slot = slot<LeggTilFil>()
        val tidspunkt = ZonedDateTime.now()
        val mediatorMock = mockk<SøknadMediator>().also {
            every { it.behandle(capture(slot)) } just Runs
            every { it.hentEier(testSoknadId) } returns defaultDummyFodselsnummer
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
  "filsti": "1111/123234",
  "storrelse": 50000,
  "ikkeibruk": "ikkeibruk",
  "urn": "urn:vedlegg:1111/123234",
  "tidspunkt": "${tidspunkt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)}"
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
                            bundlet = false,
                        ),
                        this.fil
                    )
                }
            }
        }
    }

    @Test
    fun `skal kunne besvare dokumentkrav med bundle URN`() {
        val slot = slot<DokumentKravSammenstilling>()
        val mediatorMock = mockk<SøknadMediator>().also {
            every { it.behandle(capture(slot)) } just Runs
        }

        TestApplication.withMockAuthServerAndTestApplication(
            mockedSøknadApi(
                søknadMediator = mediatorMock
            )
        ) {
            client.put("${Configuration.basePath}/soknad/$testSoknadId/dokumentasjonskrav/451/bundle") {
                autentisert()
                header(HttpHeaders.ContentType, "application/json")
                // language=JSON
                setBody(
                    """{
                        "urn": "urn:bundle:1"
                        }"""
                )
            }.let { response ->
                assertEquals(HttpStatusCode.Created, response.status)
                assertTrue(slot.isCaptured)

                with(slot.captured) {
                    assertEquals("451", this.kravId)
                    assertEquals(testSoknadId, this.søknadID())
                    assertEquals(defaultDummyFodselsnummer, this.ident())
                    assertEquals("urn:bundle:1", this.urn().toString())
                }
            }
        }
    }
}
