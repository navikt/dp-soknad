package no.nav.dagpenger.quizshow.api.routing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import no.nav.dagpenger.quizshow.api.Configuration
import no.nav.dagpenger.quizshow.api.SøknadStore
import no.nav.dagpenger.quizshow.api.configureStatusPages
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ApiTest {
    private val dummyUuid = UUID.randomUUID()
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Godta svar med gyldig input for Valg`() {
        val gyldigSvar = Svar("boolean", true)
        withTestApplication(Application::module) {
            handleRequest(HttpMethod.Put, "${Configuration.basePath}/soknad/$dummyUuid/faktum/456") {
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
        withTestApplication(Application::module) {
            handleRequest(HttpMethod.Put, "${Configuration.basePath}/soknad/$dummyUuid/faktum/456") {
                setBody(objectMapper.writeValueAsString(ugyldigSvar))
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }.apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        }
    }
}

fun Application.module() {
    val store = mockk<SøknadStore>(relaxed = true)
    install(ContentNegotiation) {
        jackson {}
    }
    configureStatusPages()
    routing {
        soknadApi(store)
    }
}
