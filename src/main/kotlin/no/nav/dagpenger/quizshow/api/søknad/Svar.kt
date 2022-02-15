package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import no.nav.helse.rapids_rivers.asLocalDate

class Svar(json: JsonNode) {

    val jsonNode: JsonNode
    val type: String

    init {
        require(json.has("type")) { " Ingen type definert, ugyldig faktumsvar" }
        require(json.has("svar")) { "Ingen svar definert, ugyldig faktumsvar" }
        jsonNode = json["svar"]
        type = json["type"].asText()
        validerType()
    }

    private fun validerType() {
        when (type) {
            "boolean" -> require(jsonNode is BooleanNode) { "Ikke gyldig boolean svar $jsonNode" }
            "flervalg" -> require(jsonNode is ArrayNode && jsonNode.all { it.isTextual }) { "Ikke gyldig flervalg svar $jsonNode" }
            "envalg" -> require(jsonNode is TextNode) { "Ikke gyldig envalg svar $jsonNode" }
            "localdate" -> require(jsonNode is TextNode && kotlin.runCatching { jsonNode.asLocalDate() }.isSuccess) { "Ikke gyldig localdate svar $jsonNode" }
            "double" -> require(jsonNode is NumericNode && kotlin.runCatching { jsonNode.asDouble() }.isSuccess) { "Ikke gyldig double svar $jsonNode" }
            "int" -> require(jsonNode is NumericNode && kotlin.runCatching { jsonNode.asInt() }.isSuccess) { "Ikke gyldig int svar $jsonNode" }
            "tekst" -> require(jsonNode is TextNode && kotlin.runCatching { jsonNode.asText() }.isSuccess) { "Ikke gyldig tekst svar $jsonNode" }
            "land" -> require(jsonNode is TextNode && kotlin.runCatching { jsonNode.asText() }.isSuccess) { "Ikke gyldig land svar $jsonNode" }
            "periode" -> validerPeriode()
            "generator" -> validerGenerator()
            else -> {
                throw IllegalArgumentException("Kjenner ikke typen $type")
            }
        }
    }

    private fun validerGenerator() {
        require(
            jsonNode is ArrayNode &&
                kotlin.runCatching {
                    jsonNode[0].forEach { templateFaktum ->
                        Svar(templateFaktum)
                    }
                }.isSuccess
        ) { "Ikke gyldig generatorsvar $jsonNode" }
    }

    private fun validerPeriode() {
        require(
            jsonNode is ObjectNode && kotlin.runCatching {
                jsonNode["fom"].asLocalDate()
                jsonNode["tom"].asLocalDate()
            }.isSuccess
        ) { "Ikke gyldig periode svar $jsonNode" }
    }
}
