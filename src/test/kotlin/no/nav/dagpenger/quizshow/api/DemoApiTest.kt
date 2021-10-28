package no.nav.dagpenger.quizshow.api

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DemoApiTest {

    @Test
    fun `skal putte fnr`() {
        var expected = "ikke denne her i affal"
        withTestApplication({ demoApi(publiser = { demo -> expected = demo }, env = mapOf("NAIS_CLUSTER_NAME" to "dev-gcp")) }) {
            handleRequest(HttpMethod.Put, "${Configuration.basePath}/demo/187689").apply {
                assertEquals(HttpStatusCode.OK, this.response.status())
                assertEquals(expected, "187689")
            }
        }
    }
}
