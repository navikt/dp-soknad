package no.nav.dagpenger.soknad

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.server.application.Application
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.dagpenger.soknad.data.søknadData
import no.nav.dagpenger.soknad.livssyklus.ferdigstilling.ferdigStiltSøknadRouteBuilder
import no.nav.dagpenger.soknad.personalia.KontonummerOppslag
import no.nav.dagpenger.soknad.personalia.PersonOppslag
import no.nav.dagpenger.soknad.personalia.personaliaRouteBuilder
import no.nav.dagpenger.soknad.status.BehandlingsstatusClient
import no.nav.security.mock.oauth2.MockOAuth2Server

object TestApplication {
    private const val TOKENX_ISSUER_ID = "tokenx"
    private const val CLIENT_ID = "dp-soknad"
    private const val AZURE_ISSUER_ID = "azuread"
    const val defaultDummyFodselsnummer = "12345"

    private val mockOAuth2Server: MockOAuth2Server by lazy {
        MockOAuth2Server().also { server ->
            server.start()
        }
    }

    internal val testTokenXToken: String by lazy {
        mockOAuth2Server.issueToken(
            issuerId = TOKENX_ISSUER_ID,
            audience = CLIENT_ID,
            claims = mapOf("pid" to defaultDummyFodselsnummer),
        ).serialize()
    }

    internal val azureAdToken: String by lazy {
        mockOAuth2Server.issueToken(
            issuerId = AZURE_ISSUER_ID,
            audience = CLIENT_ID,
        ).serialize()
    }

    internal fun getTokenXToken(pid: String): String {
        return mockOAuth2Server.issueToken(
            issuerId = TOKENX_ISSUER_ID,
            audience = CLIENT_ID,
            claims = mapOf("pid" to pid),
        ).serialize()
    }

    internal fun mockedSøknadApi(
        personOppslag: PersonOppslag = mockk(relaxed = true),
        kontonummerOppslag: KontonummerOppslag = mockk(relaxed = true),
        søknadMediator: SøknadMediator = mockk(relaxed = true),
        behandlingsstatusClient: BehandlingsstatusClient = mockk(relaxed = true),
    ): Application.() -> Unit {
        return fun Application.() {
            api(
                søknadApiRouteBuilder(
                    søknadMediator = søknadMediator,
                    behandlingsstatusClient = behandlingsstatusClient,
                ),
                personaliaRouteBuilder(
                    personOppslag,
                    kontonummerOppslag,
                ),
                ferdigStiltSøknadRouteBuilder(mockk(relaxed = true)),
                søknadDataRouteBuilder = søknadData(søknadMediator),
            )
        }
    }

    internal fun withMockAuthServerAndTestApplication(
        moduleFunction: Application.() -> Unit = mockedSøknadApi(),
        test: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        System.setProperty("token-x.client-id", CLIENT_ID)
        System.setProperty("token-x.well-known-url", "${mockOAuth2Server.wellKnownUrl(TOKENX_ISSUER_ID)}")
        System.setProperty("azure-app.client-id", CLIENT_ID)
        System.setProperty("azure-app.well-known-url", "${mockOAuth2Server.wellKnownUrl(AZURE_ISSUER_ID)}")

        return testApplication {
            application(moduleFunction)
            test()
        }
    }

    internal fun HttpRequestBuilder.autentisert(token: String = testTokenXToken) {
        this.header(HttpHeaders.Authorization, "Bearer $token")
    }

    internal suspend fun ApplicationTestBuilder.autentisert(
        endepunkt: String,
        token: String = testTokenXToken,
        httpMethod: HttpMethod = HttpMethod.Get,
        body: String? = null,
    ): HttpResponse {
        return client.request(endepunkt) {
            this.method = httpMethod
            body?.let { this.setBody(TextContent(it, ContentType.Application.Json)) }
            this.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
