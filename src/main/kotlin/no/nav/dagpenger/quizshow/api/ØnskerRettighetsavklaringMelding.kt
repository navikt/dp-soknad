package no.nav.dagpenger.quizshow.api

import no.nav.helse.rapids_rivers.JsonMessage
import java.time.LocalDateTime
import java.util.UUID

internal class ØnskerRettighetsavklaringMelding(private val fødselsnummer: String) {
    private val navn = "ønsker_rettighetsavklaring"
    private val opprettet = LocalDateTime.now()
    private val id = UUID.randomUUID()
    private val avklaringsId = UUID.randomUUID()
    private val søknadUuid = UUID.randomUUID()

    fun toJson() = JsonMessage.newMessage(
        mutableMapOf(
            "@event_name" to navn,
            "@opprettet" to opprettet,
            "@id" to id,
            "avklaringsId" to avklaringsId,
            "fødselsnummer" to fødselsnummer,
            "søknad_uuid" to søknadUuid,
        )
    ).toJson()
}
