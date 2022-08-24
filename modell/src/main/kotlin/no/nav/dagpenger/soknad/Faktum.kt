package no.nav.dagpenger.soknad

import com.fasterxml.jackson.databind.JsonNode

class Faktum(internal val json: JsonNode) {
    val id: String get() = json["id"].asText()
    val beskrivendeId: String = json["beskrivendeId"].asText()
    val sannsynliggjøresAv get() = json["sannsynliggjøresAv"].map { Faktum(it) }

    override fun equals(other: Any?): Boolean = other is Faktum && id == other.id

    override fun hashCode() = id.hashCode()
}
