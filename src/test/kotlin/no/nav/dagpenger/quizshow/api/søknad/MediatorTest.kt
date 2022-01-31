package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.dagpenger.quizshow.api.Mediator
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

class MediatorTest {
    private val testRapid = TestRapid()

    private val redisPersistence by lazy {
        val container = GenericContainer<Nothing>(DockerImageName.parse("bitnami/redis:6.2.6")).also {
            it.env = listOf("REDIS_PASSWORD=dummy")
            it.withExposedPorts(6379)
            it.start()
        }

        container.let { contaner ->
            RedisPersistence("${contaner.host}:${contaner.firstMappedPort}", "dummy")
        }
    }
    private val mediator = Mediator(testRapid, redisPersistence)

    @BeforeEach
    fun reset() {
        testRapid.reset()
    }

    @Test
    fun `publiserer ny-faktamelding på kafka`() {
        val fnr = "12345678910"
        mediator.håndter(NySøknadMelding(fnr))
        testRapid.inspektør.message(0).also {
            assertTrue(it.has("@id"))
            assertTrue(it.has("@event_name"))
            assertTrue(it.has("søknad_uuid"))
            assertEquals(fnr, it["fødselsnummer"].asText())
            assertEquals("NySøknad", it["@event_name"].asText())
        }
    }

    @Test
    fun `lese svar fra kafka`() {
        testRapid.reset()
        //language=JSON
        val message = """{
          "@event_name": "NySøknad",
          "fakta": "fakta",
          "fødselsnummer": "12345678910",
          "søknad_uuid": "123",
          "@opprettet": "2022-01-13T09:40:19.158310"
        }
        """.trimIndent()

        testRapid.sendTestMessage(message)
        mediator.hentFakta("123").let { ObjectMapper().readTree(it) }.also {
            assertEquals("fakta", it["fakta"].asText())
            assertEquals("12345678910", it["fødselsnummer"].asText())
            assertEquals("123", it["søknad_uuid"].asText())
            assertEquals("NySøknad", it["@event_name"].asText())
        }
    }
}
