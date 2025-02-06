package no.nav.dagpenger.soknad

import com.fasterxml.jackson.databind.JsonNode

class Faktum(private val json: JsonNode) {
    val id: String get() = json["id"].asText()
    val beskrivendeId: String = json["beskrivendeId"].asText()
    val svar: String? = json["svar"]?.asText()
    val generertAv: String? = json["generertAv"]?.asText()
    val sannsynliggjøresAv get() = json["sannsynliggjoresAv"].map { Faktum(it) }

    override fun equals(other: Any?): Boolean = other is Faktum && id == other.id

    override fun hashCode() = id.hashCode()

    fun originalJson() = json
}
