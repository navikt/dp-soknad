package no.nav.dagpenger.quizshow.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

internal class SøknadApiTest {

    private val jackson = jacksonObjectMapper()

    @Test
    fun `Skal starte søknad`() {

        withTestApplication({ søknadApi() }) {
            handleRequest(HttpMethod.Post, "${Configuration.basePath}/soknad").apply {
                assertEquals(HttpStatusCode.Created, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
                val content = jackson.readTree(this.response.content)
                assertDoesNotThrow { content["uuid"].asText().also { UUID.fromString(it) } }

            }
        }
    }

    @Test
    fun `Skal hente søknad seksjoner`() {

        withTestApplication({ søknadApi() }) {
            handleRequest(HttpMethod.Get, "${Configuration.basePath}/soknad/187689/neste-seksjon").apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun `Skal hente søknad subsumsjoner`() {

        withTestApplication({ søknadApi() }) {
            handleRequest(HttpMethod.Get, "${Configuration.basePath}/soknad/187689/subsumsjoner").apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun `Skal kunne lagre faktum`() {

        withTestApplication({ søknadApi() }) {
            handleRequest(HttpMethod.Put, "${Configuration.basePath}/soknad/187689/faktum/1245") {
                setBody("""{"id":1, "svar": true}""")
            }

                .apply {
                    assertEquals(HttpStatusCode.OK, this.response.status())
                    assertEquals("application/json; charset=UTF-8", this.response.headers["Content-Type"])
                }
        }
    }
}
