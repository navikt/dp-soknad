package no.nav.dagpenger.soknad.personalia

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.HttpProblem
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class PersonaliaApiTest {

    @Test
    fun `Skal avvise uautentiserte kall`() {
        TestApplication.withMockAuthServerAndTestApplication() {
            assertEquals(HttpStatusCode.Unauthorized, client.get("${Configuration.basePath}/personalia").status)
        }
    }

    @Test
    fun `Hent personalia for autentiserte kall`() {
        val mockPersonOppslag = mockk<PersonOppslag>().also {
            coEvery { it.hentPerson(TestApplication.defaultDummyFodselsnummer, any()) } returns testPerson
        }
        val mockKontonummerOppslag = mockk<KontonummerOppslag>().also {
            coEvery { it.hentKontonummer(TestApplication.defaultDummyFodselsnummer) } returns testKontonummer
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                personOppslag = mockPersonOppslag,
                kontonummerOppslag = mockKontonummerOppslag
            )
        ) {
            autentisert(
                endepunkt = "${Configuration.basePath}/personalia",
                httpMethod = HttpMethod.Get,
            ).let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("application/json; charset=UTF-8", response.headers["Content-Type"])
                assertEquals(forventetPersonalia, response.bodyAsText())
                coVerify(exactly = 1) { mockPersonOppslag.hentPerson(TestApplication.defaultDummyFodselsnummer, any()) }
            }
        }
    }

    @Test
    fun `Propagerer feil`() {
        val mockResponse = mockk<HttpResponse>(relaxed = true).also {
            every { it.status } returns HttpStatusCode.BadGateway
        }

        val mockPersonOppslag = mockk<PersonOppslag>().also {
            coEvery { it.hentPerson(TestApplication.defaultDummyFodselsnummer, any()) } throws ClientRequestException(
                mockResponse,
                "FEil"
            )
        }

        val mockKontonummerOppslag = mockk<KontonummerOppslag>().also {
            coEvery { it.hentKontonummer(TestApplication.defaultDummyFodselsnummer) } returns testKontonummer
        }
        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(
                personOppslag = mockPersonOppslag,
                kontonummerOppslag = mockKontonummerOppslag
            )
        ) {
            autentisert(
                "${Configuration.basePath}/personalia",
                httpMethod = HttpMethod.Get,
            ).let { response ->
                assertEquals(HttpStatusCode.BadGateway, response.status)
                assertEquals("application/json; charset=UTF-8", response.headers["Content-Type"])
                val problem = objectMapper.readValue(response.bodyAsText(), HttpProblem::class.java)
                assertEquals(HttpStatusCode.BadGateway.value, problem.status)
                assertEquals("urn:oppslag:personalia", problem.type.toASCIIString())
                coVerify(exactly = 1) { mockPersonOppslag.hentPerson(TestApplication.defaultDummyFodselsnummer, any()) }
            }
        }
    }

    private val forventetPersonalia: String
        get() {
            return objectMapper.writeValueAsString(Personalia(testPerson, testKontonummer))
        }

    private val testPerson: Person
        get() {
            val adresse = Adresse(
                adresselinje1 = "adresselinje1",
                adresselinje2 = "adresselinje2",
                adresselinje3 = "adresselinje3",
                postnummer = "2013",
                poststed = "Skjetten",
                landkode = "NOR",
                land = "NORGE"
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
