package no.nav.dagpenger.quizshow.api.personalia

import io.ktor.client.features.ClientRequestException
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.HttpProblem
import no.nav.dagpenger.quizshow.api.TestApplication
import no.nav.dagpenger.quizshow.api.TestApplication.autentisert
import no.nav.dagpenger.quizshow.api.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class PersonaliaApiTest {

    @Test
    fun `Skal avvise uautentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi()
        ) {
            handleRequest(HttpMethod.Get, "${Configuration.basePath}/personalia") { }.apply {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `Hent personalia for autentiserte kall`() {
        val mockPersonOppslag = mockk<PersonOppslag>().also {
            coEvery { it.hentPerson(TestApplication.defaultDummyFodselsnummer) } returns testPerson
        }
        val mockKontonummerOppslag = mockk<KontonummerOppslag>().also {
            coEvery { it.hentKontonummer(TestApplication.defaultDummyFodselsnummer) } returns testKontonummer
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(personOppslag = mockPersonOppslag, kontonummerOppslag = mockKontonummerOppslag)
        ) {
            autentisert(
                "${Configuration.basePath}/personalia",
                httpMethod = HttpMethod.Get,
            ).apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals(ContentType.Application.Json.contentType, this.response.contentType().contentType)
                assertEquals(testPersonalia, objectMapper.readValue(this.response.content!!, Personalia::class.java))
                coVerify(exactly = 1) { mockPersonOppslag.hentPerson(TestApplication.defaultDummyFodselsnummer) }
            }
        }
    }

    @Test
    fun `Propagerer feil`() {

        val mockPersonOppslag = mockk<PersonOppslag>().also {
            coEvery { it.hentPerson(TestApplication.defaultDummyFodselsnummer) } returns testPerson
        }

        val mockResponse = mockk<io.ktor.client.statement.HttpResponse>(relaxed = true).also {
            every { it.status } returns HttpStatusCode.BadGateway
        }
        val mockKontonummerOppslag = mockk<KontonummerOppslag>().also {
            coEvery { it.hentKontonummer(TestApplication.defaultDummyFodselsnummer) } throws ClientRequestException(
                mockResponse,
                "FEil"
            )
        }
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(personOppslag = mockPersonOppslag, kontonummerOppslag = mockKontonummerOppslag)
        ) {
            autentisert(
                "${Configuration.basePath}/personalia",
                httpMethod = HttpMethod.Get,
            ).apply {
                assertEquals(HttpStatusCode.BadGateway, this.response.status())
                assertEquals(ContentType.Application.Json.contentType, this.response.contentType().contentType)
                val problem = objectMapper.readValue(this.response.content!!, HttpProblem::class.java)
                assertEquals(HttpStatusCode.BadGateway.value, problem.status)
                assertEquals("urn:oppslag:personalia", problem.type.toASCIIString())
                coVerify(exactly = 1) { mockPersonOppslag.hentPerson(TestApplication.defaultDummyFodselsnummer) }
            }
        }
    }

    private val testPersonalia = Personalia(testPerson, testKontonummer)

    private val testPerson: Person
        get() {
            val adresse = Adresse(
                adresselinje1 = "adresselinje1",
                adresselinje2 = "adresselinje2",
                adresselinje3 = "adresselinje3",
                byEllerStedsnavn = "byEllerStedsnavn",
                landkode = "NOR",
                land = "Norge",
                postkode = "2013"
            )
            return Person(
                forNavn = "forNavn",
                mellomNavn = "mellonNavn",
                etterNavn = "etterNavn",
                fødselsDato = LocalDate.of(2000, 5, 1),
                postAdresse = adresse,
                folkeregistrertAdresse = adresse
            )
        }
    private val testKontonummer: Kontonummer
        get() {
            return Kontonummer("12345677889", "Banken", "SWE")
        }
}