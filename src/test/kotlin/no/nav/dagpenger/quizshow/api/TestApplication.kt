package no.nav.dagpenger.quizshow.api

import io.ktor.application.Application
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import no.nav.dagpenger.quizshow.api.auth.AuthFactory
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

    internal fun mockedSøknadApi(
        store: SøknadStore = mockk(relaxed = true)
    ): Application.() -> Unit {

        return fun Application.() {
            søknadApi(
                jwkProvider = AuthFactory.jwkProvider,
                issuer = AuthFactory.issuer,
                clientId = AuthFactory.clientId,
                store = store
            )
        }
    }

    internal fun <R> withMockAuthServerAndTestApplication(
        moduleFunction: Application.() -> Unit,
        test: TestApplicationEngine.() -> R
    ): R {
        try {
            System.setProperty("token-x.client-id", CLIENT_ID)
            System.setProperty("token-x.well-known-url", "${mockOAuth2Server.wellKnownUrl(ISSUER_ID)}")

            return withTestApplication(moduleFunction, test)
        } finally {
        }
    }

    internal fun TestApplicationEngine.autentisert(
        endepunkt: String,
        token: String = testOAuthToken,
        httpMethod: HttpMethod = HttpMethod.Get,
        body: String? = null
    ) = handleRequest(httpMethod, endepunkt) {
        addHeader(
            HttpHeaders.Accept,
            ContentType.Application.Json.toString()
        )
        addHeader(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString()
        )
        addHeader(HttpHeaders.Authorization, "Bearer $token")
        body?.also { setBody(it) }
    }
}
