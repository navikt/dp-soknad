package no.nav.dagpenger.soknad.status

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.soknad.Configuration
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.TestApplication
import no.nav.dagpenger.soknad.TestApplication.autentisert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class StatusApiTest {

    @Test
    fun `returnerer status til søknad`() {

        val søknadUuid = UUID.randomUUID()
        val søknadTilstand = Påbegynt

        val mockSøknadMediator = mockk<SøknadMediator>().also {
            every { it.hentEier(søknadUuid) } returns TestApplication.defaultDummyFodselsnummer
            every { it.hentTilstand(søknadUuid) } returns søknadTilstand
        }

        TestApplication.withMockAuthServerAndTestApplication(
            TestApplication.mockedSøknadApi(søknadMediator = mockSøknadMediator)
        ) {
            val endepunkt = "${Configuration.basePath}/soknad/$søknadUuid/status"
            autentisert(
                endepunkt,
                httpMethod = HttpMethod.Get
            ).apply {
                assertEquals(HttpStatusCode.OK, this.status)
                val tilstand: String = client.get(endepunkt).bodyAsText()
                println("Tilstand: $tilstand")
            }
        }
    }

    @Test
    fun `returnerer 404 når søknad ikke eksisterer`() {
    }
}
