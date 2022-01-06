package no.nav.dagpenger.quizshow.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import no.nav.dagpenger.quizshow.api.TestApplication.handleAuthenticatedRequest
import no.nav.dagpenger.quizshow.api.TestApplication.mockedSøknadApi
import no.nav.dagpenger.quizshow.api.TestApplication.withMockAuthServerAndTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ApiTest {
    private val dummyUuid = UUID.randomUUID()
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Skal avvise uauthentiserte kall`() {
        withMockAuthServerAndTestApplication(
            mockedSøknadApi()
        ) {
            handleRequest(HttpMethod.Get, "${Configuration.basePath}/soknad/$dummyUuid/subsumsjoner") {
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }.apply {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        }
    }

    @Test
    fun `Godta svar med gyldig input for Valg`() {
        val gyldigSvar = Svar("boolean", true)
        withMockAuthServerAndTestApplication(
            mockedSøknadApi()
        ) {
            handleAuthenticatedRequest(HttpMethod.Put, "${Configuration.basePath}/soknad/$dummyUuid/faktum/456") {
                setBody(objectMapper.writeValueAsString(gyldigSvar))
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun `Avvis svar av typen Valg dersom ingen alternativer er valgt`() {
        val ugyldigSvar = Svar("valg", emptyList<String>())
        withMockAuthServerAndTestApplication(
            mockedSøknadApi()
        ) {
            handleAuthenticatedRequest(HttpMethod.Put, "${Configuration.basePath}/soknad/$dummyUuid/faktum/456") {
                setBody(objectMapper.writeValueAsString(ugyldigSvar))
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        }
    }
}
