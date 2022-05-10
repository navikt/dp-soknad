package no.nav.dagpenger.soknad

import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.server.application.Application
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.dagpenger.soknad.auth.AuthFactory
import no.nav.dagpenger.soknad.personalia.KontonummerOppslag
import no.nav.dagpenger.soknad.personalia.PersonOppslag
import no.nav.dagpenger.soknad.personalia.personaliaRouteBuilder
import no.nav.dagpenger.soknad.søknad.SøknadStore
import no.nav.security.mock.oauth2.MockOAuth2Server

object TestApplication {
    private const val ISSUER_ID = "default"
    private const val CLIENT_ID = "default"
    const val defaultDummyFodselsnummer = "12345"

    private val mockOAuth2Server: MockOAuth2Server by lazy {
        MockOAuth2Server().also { server ->
            server.start()
        }
    }

    private val testOAuthToken: String by lazy {
        mockOAuth2Server.issueToken(
            issuerId = ISSUER_ID,
            claims = mapOf("pid" to defaultDummyFodselsnummer)
        ).serialize()
    }

    internal fun getToken(pid: String): String {
        return mockOAuth2Server.issueToken(
            issuerId = ISSUER_ID,
            claims = mapOf("pid" to pid)
        ).serialize()
    }

    internal fun mockedSøknadApi(
        store: SøknadStore = mockk(relaxed = true),
        personOppslag: PersonOppslag = mockk(relaxed = true),
        kontonummerOppslag: KontonummerOppslag = mockk(relaxed = true),
        søknadMediator: SøknadMediator = mockk(relaxed = true)
    ): Application.() -> Unit {

        return fun Application.() {
            api(
                jwkProvider = AuthFactory.jwkProvider,
                issuer = AuthFactory.issuer,
                clientId = AuthFactory.clientId,
                store = store,
                søknadMediator,
                personaliaRouteBuilder(personOppslag, kontonummerOppslag)
            )
        }
    }

    internal fun withMockAuthServerAndTestApplication(
        moduleFunction: Application.() -> Unit = mockedSøknadApi(),
        test: suspend ApplicationTestBuilder.() -> Unit
    ) {
        System.setProperty("token-x.client-id", CLIENT_ID)
        System.setProperty("token-x.well-known-url", "${mockOAuth2Server.wellKnownUrl(ISSUER_ID)}")

        return testApplication {
            application(moduleFunction)
            test()
        }
    }

    internal suspend fun ApplicationTestBuilder.autentisert(
        endepunkt: String,
        token: String = testOAuthToken,
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
