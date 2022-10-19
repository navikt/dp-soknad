package no.nav.dagpenger.soknad.mineSoknader

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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class MineSoknaderApiTest {

    private val søknadUuid = UUID.randomUUID()
    private val endepunkt = "${Configuration.basePath}/soknad/mineSoknader"

    @Test
    fun `én påbegynt og to innsendte søknader`() {
        val opprettet = LocalDateTime.MAX
        val innsendtTidspunkt = LocalDateTime.MAX

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentSøknader(TestApplication.defaultDummyFodselsnummer) } returns setOf(
                        søknadMed(tilstand = Påbegynt, opprettet),
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
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson =
                    expectedJson(opprettet, innsendtTidspunkt)
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
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentSøknader(TestApplication.defaultDummyFodselsnummer) } returns emptySet()
                }
            )
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson = """{}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `én påbegynt og ingen innsendte`() {
        val opprettet = LocalDateTime.MAX

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentSøknader(TestApplication.defaultDummyFodselsnummer) } returns setOf(
                        søknadMed(tilstand = Påbegynt, opprettet)
                    )
                }
            )
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson = """{"paabegynt":{"soknadUuid":"$søknadUuid","opprettet":"$opprettet"}}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `én innsendt og ingen påbegynte`() {
        val innsendtTidspunkt = LocalDateTime.MAX

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator = mockk<SøknadMediator>().also {
                    every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
                    every { it.hentSøknader(TestApplication.defaultDummyFodselsnummer) } returns setOf(
                        søknadMed(
                            tilstand = Innsendt,
                            innsending = innsending(innsendtTidspunkt, journalpostId = "456")
                        )
                    )
                }
            )
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson =
                    """{"innsendte":[{"soknadUuid":"$søknadUuid","forstInnsendt":"$innsendtTidspunkt"}]}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    private fun expectedJson(opprettet: LocalDateTime?, innsendtTidspunkt: LocalDateTime?) =
        //language=JSON
        """{"paabegynt":{"soknadUuid":"$søknadUuid","opprettet":"$opprettet"},"innsendte":[{"soknadUuid":"$søknadUuid","forstInnsendt":"$innsendtTidspunkt"},{"soknadUuid":"$søknadUuid","forstInnsendt":"$innsendtTidspunkt"}]}"""

    private fun søknadMed(
        tilstand: Søknad.Tilstand.Type,
        opprettet: LocalDateTime = LocalDateTime.now(),
        innsending: NyInnsending? = null,
    ) = Søknad.rehydrer(
        søknadId = søknadUuid,
        ident = TestApplication.defaultDummyFodselsnummer,
        opprettet = ZonedDateTime.of(opprettet, ZoneId.of("Europe/Oslo")),
        språk = Språk("NO"),
        dokumentkrav = Dokumentkrav(),
        sistEndretAvBruker = ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("Europe/Oslo")),
        tilstandsType = tilstand,
        aktivitetslogg = Aktivitetslogg(),
        innsending = innsending
    )

    private fun innsending(
        innsendtTidspunkt: LocalDateTime,
        journalpostId: String,
        ettersending: List<Ettersending> = emptyList(),
    ) = NyInnsending.rehydrer(
        innsendingId = UUID.randomUUID(),
        type = Innsending.InnsendingType.NY_DIALOG,
        innsendt = ZonedDateTime.of(innsendtTidspunkt, ZoneId.of("Europe/Oslo")),
        journalpostId = journalpostId,
        tilstandsType = Innsending.TilstandType.Journalført,
        hovedDokument = null,
        dokumenter = emptyList(),
        ettersendinger = ettersending,
        brevkode = Innsending.Brevkode("04-02-03")
    )
}
