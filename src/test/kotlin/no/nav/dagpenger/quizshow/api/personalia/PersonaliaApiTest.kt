package no.nav.dagpenger.quizshow.api.personalia

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.dagpenger.quizshow.api.Configuration
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

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(personOppslag = mockPersonOppslag)
        ) {
            autentisert(
                "${Configuration.basePath}/personalia",
                httpMethod = HttpMethod.Get,
            ).apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals(ContentType.Application.Json.contentType, this.response.contentType().contentType)
                assertEquals(testPerson, objectMapper.readValue(this.response.content!!, Person::class.java))
                coVerify(exactly = 1) { mockPersonOppslag.hentPerson(TestApplication.defaultDummyFodselsnummer) }
            }
        }
    }

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
                mellonNavn = "mellonNavn",
                etterNavn = "etterNavn",
                fødselsDato = LocalDate.of(2000, 5, 1),
                postAdresse = adresse,
                folkeregistrertAdresse = adresse
            )
        }
}
