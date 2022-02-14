package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import java.time.LocalDateTime
import java.util.UUID

internal class FaktumSvar(
    private val søknadUuid: UUID,
    private val faktumId: String,
    private val clazz: String,
    private val svar: JsonNode
) {

    private val navn = "faktum_svar"
    private val opprettet = LocalDateTime.now()
    private val id = UUID.randomUUID()

    fun søknadUuid() = søknadUuid

    fun toJson() = JsonMessage.newMessage(
        mutableMapOf(
            "@event_name" to navn,
            "@opprettet" to opprettet,
            "@id" to id,
            "fakta" to listOf(
                mapOf(
                    "id" to faktumId,
                    "clazz" to clazz,
                    "svar" to svar
                )
            ),
            "søknad_uuid" to søknadUuid,
        )
    ).toJson()
}
