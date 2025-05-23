package no.nav.dagpenger.soknad.minesoknader

import FerdigSøknadData
import de.slub.urn.URN
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Prosessnavn
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.DEAFULT_DUMMY_FNR
import no.nav.dagpenger.soknad.TestApplication.autentisert
import no.nav.dagpenger.soknad.faktumJson
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class MineSøknaderApiTest {
    private val søknadUuid = UUID.randomUUID()
    private val endepunkt = "${Configuration.basePath}/soknad/mine-soknader"
    private val endepunktIncludeDokumentkrav = "${Configuration.basePath}/soknad/mine-soknader?include=dokumentkrav"
    private val nå = LocalDate.now()

    private val dokumentfaktum1 = Faktum(faktumJson(id = "1", beskrivendeId = "f1", generertAv = "foo"))
    private val dokumentfaktum2 = Faktum(faktumJson(id = "2", beskrivendeId = "f2", generertAv = "foo"))
    private val faktaSomSannsynliggjøres = mutableSetOf(Faktum(faktumJson(id = "2", beskrivendeId = "f2")))

    private val sannsynliggjøring1 =
        Sannsynliggjøring(
            id = dokumentfaktum1.id,
            faktum = dokumentfaktum1,
            sannsynliggjør = faktaSomSannsynliggjøres,
        )

    private val sannsynliggjøring2 =
        Sannsynliggjøring(
            id = dokumentfaktum2.id,
            faktum = dokumentfaktum2,
            sannsynliggjør = faktaSomSannsynliggjøres,
        )

    private val fil =
        Krav.Fil(
            filnavn = "testfil.jpg",
            urn = URN.rfc8141().parse("urn:nav:1"),
            storrelse = 89900,
            tidspunkt = ZonedDateTime.now(),
            bundlet = false,
        )

    private val krav1 =
        Krav(
            id = dokumentfaktum1.id,
            svar =
                Krav.Svar(
                    filer = mutableSetOf(fil),
                    valg = Krav.Svar.SvarValg.SEND_NÅ,
                    begrunnelse = null,
                    bundle = URN.rfc8141().parse("urn:bundle:1"),
                    innsendt = true,
                ),
            sannsynliggjøring = sannsynliggjøring1,
            tilstand = Krav.KravTilstand.AKTIV,
        )

    private val krav2 =
        Krav(
            id = dokumentfaktum2.id,
            svar =
                Krav.Svar(
                    filer = mutableSetOf(),
                    valg = Krav.Svar.SvarValg.SENDER_IKKE,
                    begrunnelse = "Har ikke dokumentet tilgjengelig",
                    bundle = null,
                    innsendt = true,
                ),
            sannsynliggjøring = sannsynliggjøring2,
            tilstand = Krav.KravTilstand.AKTIV,
        )

    private val dokumentkrav =
        Dokumentkrav.rehydrer(
            setOf(krav1, krav2),
        )

    @Test
    fun `én påbegynt og to innsendte søknader`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentSøknader(DEAFULT_DUMMY_FNR) } returns
                            setOf(
                                søknadMed(tilstand = Påbegynt),
                                søknadMed(tilstand = Innsendt),
                                søknadMed(tilstand = Innsendt),
                            )
                    },
            ),
        ) {
            autentisert("$endepunkt?fom=$nå", httpMethod = HttpMethod.Get).also { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())

                val jsonResponse = objectMapper.readTree(response.bodyAsText())
                val påbegynt = jsonResponse["paabegynt"]
                val innsendte = jsonResponse["innsendte"]

                assertNotNull(påbegynt)
                assertEquals(2, innsendte.size())
            }
        }
    }

    @Test
    fun `Skal ikke hente ut generelle innsendinger`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentSøknader(DEAFULT_DUMMY_FNR) } returns
                            setOf(
                                søknadMed(
                                    tilstand = Påbegynt,
                                    prosessversjon = Prosessversjon(Prosessnavn("Innsending"), 1),
                                ),
                                søknadMed(
                                    tilstand = Innsendt,
                                    prosessversjon = Prosessversjon(Prosessnavn("Innsending"), 1),
                                ),
                            )
                    },
            ),
        ) {
            autentisert("$endepunkt?fom=$nå", httpMethod = HttpMethod.Get).also { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())

                val jsonResponse = objectMapper.readTree(response.bodyAsText())
                assertTrue(jsonResponse.isEmpty)
            }
        }
    }

    @Test
    fun `ingen søknader`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentSøknader(DEAFULT_DUMMY_FNR) } returns emptySet()
                    },
            ),
        ) {
            autentisert("$endepunkt?fom=$nå", httpMethod = HttpMethod.Get).also { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())

                val jsonResponse = objectMapper.readTree(response.bodyAsText())
                assertTrue(jsonResponse.isEmpty)
            }
        }
    }

    @Test
    fun `én innsendt og ingen påbegynte søknader`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentSøknader(DEAFULT_DUMMY_FNR) } returns
                            setOf(
                                søknadMed(tilstand = Innsendt),
                            )
                    },
            ),
        ) {
            autentisert("$endepunkt?fom=$nå", httpMethod = HttpMethod.Get).also { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())

                val jsonResponse = objectMapper.readTree(response.bodyAsText())
                val påbegynt = jsonResponse["paabegynt"]
                val innsendte = jsonResponse["innsendte"]

                assertNull(påbegynt)
                assertEquals(1, innsendte.size())
            }
        }
    }

    @Test
    fun `tar ikke med søknader som er innsendt før fom queryparam`() {
        val gammelInnsendtSøknad =
            søknadMed(
                tilstand = Innsendt,
                innsendt = nå.atStartOfDay().minusDays(2).atZone(ZoneId.of("Europe/Oslo")),
            )

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentSøknader(DEAFULT_DUMMY_FNR) } returns
                            setOf(
                                søknadMed(tilstand = Innsendt),
                                gammelInnsendtSøknad,
                            )
                    },
            ),
        ) {
            autentisert("$endepunkt?fom=$nå", httpMethod = HttpMethod.Get).also { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())

                val jsonResponse = objectMapper.readTree(response.bodyAsText())
                val innsendte = jsonResponse["innsendte"]

                assertEquals(1, innsendte.size())
            }
        }
    }

    @Test
    fun `får 400 Bad Request ved manglende eller ugyldig fom queryparam`() {
        TestApplication.withMockAuthServerAndTestApplication(TestApplication.mockedSøknadApi()) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.BadRequest, this.status)
            }
            autentisert("$endepunkt?fom=ugyldigFom", httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.BadRequest, this.status)
            }
        }
    }

    @Test
    fun `henter bare ut dokumentkrav hvis queryparam include=dokumentkrav`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentSøknader(DEAFULT_DUMMY_FNR) } returns
                            setOf(
                                søknadMed(
                                    tilstand = Innsendt,
                                    dokumentkrav = dokumentkrav,
                                ),
                            )
                    },
            ),
        ) {
            autentisert("$endepunktIncludeDokumentkrav&fom=$nå", httpMethod = HttpMethod.Get).let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())

                val jsonResponse = objectMapper.readTree(response.bodyAsText())
                val dokumentkrav = jsonResponse["innsendte"][0]["dokumentkrav"]

                assertEquals(2, dokumentkrav.size())
            }

            autentisert("$endepunkt?fom=$nå", httpMethod = HttpMethod.Get).let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.contentType().toString())

                val jsonResponse = objectMapper.readTree(response.bodyAsText())
                val dokumentkrav = jsonResponse["innsendte"][0]["dokumentkrav"]

                assertNull(dokumentkrav)
            }
        }
    }

    @Test
    fun `sjekker at 'toMineSoknaderDokumentkravDTO()' mapper feltene riktig`() {
        val mineSoknaderDokumenktravDTO = dokumentkrav.aktiveDokumentKrav().toMineSoknaderDokumentkravDTO()

        val dokumenktravMedFil = mineSoknaderDokumenktravDTO.first()
        val dokumentkravUtenFil = mineSoknaderDokumenktravDTO.last()

        with(dokumenktravMedFil) {
            assertEquals(dokumentfaktum1.id, this.id)
            assertEquals(dokumentfaktum1.beskrivendeId, this.beskrivendeId)
            assertNull(this.begrunnelse)
            assertEquals(fil.filnavn, this.filer.first())
            assertEquals(Krav.Svar.SvarValg.SEND_NÅ.name, this.valg)
            assertEquals("1", this.bundleFilsti)
        }

        with(dokumentkravUtenFil) {
            assertEquals(dokumentfaktum2.id, this.id)
            assertEquals(dokumentfaktum2.beskrivendeId, this.beskrivendeId)
            assertEquals("Har ikke dokumentet tilgjengelig", this.begrunnelse)
            assertTrue(this.filer.isEmpty())
            assertEquals(Krav.Svar.SvarValg.SENDER_IKKE.name, this.valg)
            assertNull(this.bundleFilsti)
        }
    }

    private fun søknadMed(
        tilstand: Søknad.Tilstand.Type,
        opprettet: LocalDateTime = LocalDateTime.now(),
        innsendt: ZonedDateTime? = LocalDateTime.MAX.atZone(ZoneId.of("Europe/Oslo")),
        dokumentkrav: Dokumentkrav = Dokumentkrav(),
        sistEndretAvBruker: LocalDateTime = LocalDateTime.MAX,
        prosessversjon: Prosessversjon? = Prosessversjon(Prosessnavn("Dagpenger"), 1),
    ) = Søknad.rehydrer(
        søknadId = søknadUuid,
        ident = DEAFULT_DUMMY_FNR,
        opprettet = ZonedDateTime.of(opprettet, ZoneId.of("Europe/Oslo")),
        innsendt = innsendt,
        språk = Språk("NO"),
        dokumentkrav = dokumentkrav,
        sistEndretAvBruker = ZonedDateTime.of(sistEndretAvBruker, ZoneId.of("Europe/Oslo")),
        tilstandsType = tilstand,
        aktivitetslogg = Aktivitetslogg(),
        prosessversjon = prosessversjon,
        data = FerdigSøknadData,
    )
}
