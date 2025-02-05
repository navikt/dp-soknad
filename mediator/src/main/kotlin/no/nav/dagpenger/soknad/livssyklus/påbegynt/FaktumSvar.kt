package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import java.time.LocalDateTime
import java.util.UUID

internal class FaktumSvar(
    private val søknadUuid: UUID,
    private val faktumId: String,
    private val type: String,
    private val ident: String,
    private val svar: JsonNode,
    private val besvart: LocalDateTime = LocalDateTime.now(),
) {
    private val navn = "faktum_svar"
    private val opprettet = LocalDateTime.now()
    private val id = UUID.randomUUID()

    fun søknadUuid() = søknadUuid
    fun ident() = ident
    fun besvart() = besvart

    fun toJson() = JsonMessage.newMessage(
        mapOf(
            "@event_name" to navn,
            "@opprettet" to opprettet,
            "@id" to id,
            "besvart" to besvart,
            "fakta" to listOf(
                mapOf(
                    "id" to faktumId,
                    "type" to type,
                    "svar" to svar,
                ),
            ),
            "søknad_uuid" to søknadUuid,
        ),
    ).toJson()
}
