package no.nav.dagpenger.quizshow.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class FaktumSvarTest {
    private val jackson = jacksonObjectMapper()
    @Test
    fun ` Skal lage faktum_svar event  `() {
        FaktumSvar(
            søknadUuid = UUID.randomUUID(),
            faktumId = "1",
            clazz = "localdate",
            svar = "true"
        ).toJson().also {
            val content = jackson.readTree(it)
            assertNotNull(content["@id"])
            assertNotNull(content["@opprettet"])
            assertNotNull(content["søknad_uuid"])
            assertEquals("faktum_svar", content["@event_name"].asText())
            assertNotNull(content["fakta"])
            assertEquals("1", content["fakta"][0]["id"].asText())
            assertEquals("localdate", content["fakta"][0]["clazz"].asText())
            assertEquals("true", content["fakta"][0]["svar"].asText())
        }
    }
}
