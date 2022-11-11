package no.nav.dagpenger.soknad.minesoknader

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Ettersending
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.NyInnsending
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class MineSøknaderApiTest {
    private val søknadUuid = UUID.randomUUID()
    private val endepunkt = "${Configuration.basePath}/soknad/mine-soknader"

    @Test
    fun `én påbegynt og to innsendte søknader`() {
        val opprettet = LocalDateTime.MAX
        val innsendtTidspunkt = LocalDateTime.MAX
        val sistEndretAvBruker = LocalDateTime.MAX

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentSøknader(TestApplication.defaultDummyFodselsnummer) } returns setOf(
                        søknadMed(tilstand = Påbegynt, opprettet, sistEndretAvBruker = sistEndretAvBruker),
                        søknadMed(
                            tilstand = Innsendt,
                            innsending = innsending(innsendtTidspunkt, journalpostId = "123")
                        ),
                        søknadMed(
                            tilstand = Innsendt,
                            innsending = innsending(innsendtTidspunkt, journalpostId = "456")
                        )
                    )
                }
            )
        ) {
            val fom = LocalDate.now()
            autentisert("$endepunkt?fom=$fom", httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson = expectedJson(opprettet, innsendtTidspunkt, sistEndretAvBruker)
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `ingen søknader`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentSøknader(TestApplication.defaultDummyFodselsnummer) } returns emptySet()
                }
            )
        ) {
            val fom = LocalDate.now()
            autentisert("$endepunkt?fom=$fom", httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson = """{}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `én innsendt og ingen påbegynte søknader`() {
        val innsendtTidspunkt = LocalDateTime.MAX

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentSøknader(TestApplication.defaultDummyFodselsnummer) } returns setOf(
                        søknadMed(
                            tilstand = Innsendt,
                            innsending = innsending(innsendtTidspunkt, journalpostId = "456")
                        )
                    )
                }
            )
        ) {
            val fom = LocalDate.now()
            autentisert("$endepunkt?fom=$fom", httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson =
                    """{"innsendte":[{"soknadUuid":"$søknadUuid","forstInnsendt":"$innsendtTidspunkt"}]}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `tar ikke med søknader som er innsendt før fom queryparam`() {
        val innsendtTidspunkt = LocalDateTime.MAX
        val fom = LocalDate.now()

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentSøknader(TestApplication.defaultDummyFodselsnummer) } returns setOf(
                        søknadMed(
                            tilstand = Innsendt,
                            innsending = innsending(innsendtTidspunkt, journalpostId = "456")
                        ),
                        gammelInnsendtSøknad(innsendt = fom.minusDays(2))
                    )
                }
            )
        ) {
            autentisert("$endepunkt?fom=$fom", httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson =
                    """{"innsendte":[{"soknadUuid":"$søknadUuid","forstInnsendt":"$innsendtTidspunkt"}]}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
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

    private fun gammelInnsendtSøknad(innsendt: LocalDate) = søknadMed(
        tilstand = Innsendt,
        innsending = innsending(innsendt.minusDays(5).atStartOfDay(), journalpostId = "456")
    )

    private fun expectedJson(
        opprettet: LocalDateTime?,
        innsendtTidspunkt: LocalDateTime?,
        sistEndretAvBruker: LocalDateTime
    ) =
        //language=JSON
        """{"paabegynt":{"soknadUuid":"$søknadUuid","opprettet":"$opprettet","sistEndretAvBruker":"$sistEndretAvBruker"},"innsendte":[{"soknadUuid":"$søknadUuid","forstInnsendt":"$innsendtTidspunkt"},{"soknadUuid":"$søknadUuid","forstInnsendt":"$innsendtTidspunkt"}]}"""

    private fun søknadMed(
        tilstand: Søknad.Tilstand.Type,
        opprettet: LocalDateTime = LocalDateTime.now(),
        innsending: NyInnsending? = null,
        sistEndretAvBruker: LocalDateTime = LocalDateTime.MAX
    ) = Søknad.rehydrer(
        søknadId = søknadUuid,
        ident = TestApplication.defaultDummyFodselsnummer,
        opprettet = ZonedDateTime.of(opprettet, ZoneId.of("Europe/Oslo")),
        språk = Språk("NO"),
        dokumentkrav = Dokumentkrav(),
        sistEndretAvBruker = ZonedDateTime.of(sistEndretAvBruker, ZoneId.of("Europe/Oslo")),
        tilstandsType = tilstand,
        aktivitetslogg = Aktivitetslogg(),
        innsending = innsending,
        prosessversjon = null,
        versjon = 1
    )

    private fun innsending(
        innsendtTidspunkt: LocalDateTime,
        journalpostId: String,
        ettersending: List<Ettersending> = emptyList()
    ) = NyInnsending.rehydrer(
        innsendingId = UUID.randomUUID(),
        type = Innsending.InnsendingType.NY_DIALOG,
        innsendt = ZonedDateTime.of(innsendtTidspunkt, ZoneId.of("Europe/Oslo")),
        journalpostId = journalpostId,
        tilstandsType = Innsending.TilstandType.Journalført,
        hovedDokument = null,
        dokumenter = emptyList(),
        ettersendinger = ettersending,
        metadata = Innsending.Metadata("04-02-03")
    )
}
