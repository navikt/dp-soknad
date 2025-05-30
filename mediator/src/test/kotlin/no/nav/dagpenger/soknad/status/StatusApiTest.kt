package no.nav.dagpenger.soknad.status

import FerdigSøknadData
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.contentType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.HttpProblem
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Innsendt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.UnderOpprettelse
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.DEAFULT_DUMMY_FNR
import no.nav.dagpenger.soknad.TestApplication.autentisert
import no.nav.dagpenger.soknad.status.SøknadStatus.Paabegynt
import no.nav.dagpenger.soknad.status.SøknadStatus.Ukjent
import no.nav.dagpenger.soknad.status.SøknadStatus.UnderBehandling
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class StatusApiTest {
    private val søknadUuid = UUID.randomUUID()
    private val endepunkt = "${Configuration.basePath}/soknad/$søknadUuid/status"

    @Test
    fun `Status på søknad med tilstand Påbegynt`() {
        val opprettet = LocalDateTime.MAX
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentEier(søknadUuid) } returns DEAFULT_DUMMY_FNR
                        every { it.hent(søknadUuid) } returns søknadMed(tilstand = Påbegynt, opprettet)
                    },
            ),
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson = """{"status":"$Paabegynt","opprettet":"$opprettet"}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `Status på søknad med tilstand UnderOpprettelse`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentEier(søknadUuid) } returns DEAFULT_DUMMY_FNR
                        every { it.hent(søknadUuid) } returns søknadMed(tilstand = UnderOpprettelse)
                    },
            ),
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(BadRequest, this.status)
            }
        }
    }

    @Test
    fun `Søknad med tilstand Innsendt og status UnderBehandling`() {
        val opprettet = LocalDateTime.MAX
        val innsendt = LocalDateTime.of(2022, 1, 1, 12, 15, 30).atZone(ZoneId.of("Europe/Oslo"))

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentEier(søknadUuid) } returns DEAFULT_DUMMY_FNR
                        every { it.hent(søknadUuid) } returns søknadMed(tilstand = Innsendt, opprettet, innsendt)
                    },
                behandlingsstatusClient =
                    mockk<BehandlingsstatusClient>().also {
                        coEvery {
                            it.hentBehandlingsstatus(
                                any(),
                                any(),
                            )
                        } returns BehandlingsstatusDto(behandlingsstatus = "UnderBehandling")
                    },
            ),
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson = """{"status":"$UnderBehandling","opprettet":"$opprettet","innsendt":"${innsendt.toLocalDateTime()}"}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `Søknad med tilstand Innsendt og status null gir Ukjent`() {
        val opprettet = LocalDateTime.MAX
        val innsendt =
            LocalDateTime.of(2022, 1, 1, 12, 15, 30)
                .atZone(ZoneId.of("Europe/Oslo"))

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentEier(søknadUuid) } returns DEAFULT_DUMMY_FNR
                        every { it.hent(søknadUuid) } returns søknadMed(tilstand = Innsendt, opprettet, innsendt)
                    },
                behandlingsstatusClient =
                    mockk<BehandlingsstatusClient>().also {
                        coEvery {
                            it.hentBehandlingsstatus(
                                any(),
                                any(),
                            )
                        } returns BehandlingsstatusDto(behandlingsstatus = "Ukjent")
                    },
            ),
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val expectedJson = """{"status":"$Ukjent","opprettet":"$opprettet","innsendt":"${innsendt.toLocalDateTime()}"}"""
                assertEquals(expectedJson, this.bodyAsText())
                assertEquals("application/json; charset=UTF-8", this.contentType().toString())
            }
        }
    }

    @Test
    fun `Skal avvise autentiserte kall der pid på token ikke er eier av søknaden`() {
        val mediatorMock =
            mockk<SøknadMediator>().also {
                every { it.hentEier(søknadUuid) } returns "annen eier"
            }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(søknadMediator = mediatorMock),
        ) {
            assertEquals(HttpStatusCode.Forbidden, autentisert(endepunkt).status)
        }
    }

    @Test
    fun `returnerer 404 når søknad ikke eksisterer`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                søknadMediator =
                    mockk<SøknadMediator>().also {
                        every { it.hentEier(søknadUuid) } returns DEAFULT_DUMMY_FNR
                        every { it.hent(søknadUuid) } returns null
                    },
            ),
        ) {
            autentisert(endepunkt, httpMethod = HttpMethod.Get).apply {
                assertEquals(NotFound, this.status)
                val problem = this.bodyAsText().let { objectMapper.readValue<HttpProblem>(it) }
                problem.detail shouldBe "Søknad med id $søknadUuid ikke funnet"
            }
        }
    }

    private fun søknadMed(
        tilstand: Søknad.Tilstand.Type,
        opprettet: LocalDateTime = LocalDateTime.now(),
        innsendt: ZonedDateTime? = null,
    ) = Søknad.rehydrer(
        søknadId = søknadUuid,
        ident = DEAFULT_DUMMY_FNR,
        opprettet = ZonedDateTime.of(opprettet, ZoneId.of("Europe/Oslo")),
        innsendt = innsendt,
        språk = Språk("NO"),
        dokumentkrav = Dokumentkrav(),
        sistEndretAvBruker = ZonedDateTime.of(LocalDateTime.now(), ZoneId.of("Europe/Oslo")),
        tilstandsType = tilstand,
        aktivitetslogg = Aktivitetslogg(),
        prosessversjon = null,
        data = FerdigSøknadData,
    )
}
