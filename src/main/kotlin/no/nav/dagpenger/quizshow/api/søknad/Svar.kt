package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.isMissingOrNull

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
            "flervalg" -> require(jsonNode.isArray && jsonNode.all { it.isTextual } && jsonNode.size() > 0) { "Ikke gyldig flervalg svar $jsonNode" }
            "envalg" -> require(
                jsonNode.isTextual && jsonNode.asText().isNotBlank()
            ) { "Ikke gyldig envalg svar $jsonNode" }
            "localdate" -> require(jsonNode.isTextual && kotlin.runCatching { jsonNode.asLocalDate() }.isSuccess) { "Ikke gyldig localdate svar $jsonNode" }
            "double" -> require(jsonNode.isDouble) { "Ikke gyldig double svar $jsonNode" }
            "int" -> require(jsonNode.isInt) { "Ikke gyldig int svar $jsonNode" }
            "tekst" -> require(jsonNode.isTextual) { "Ikke gyldig tekst svar $jsonNode" }
            "land" -> require(jsonNode.isTextual && jsonNode.asText().length < 4) { "Ikke gyldig land svar $jsonNode" }
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
                    jsonNode[0].forEach { faktum ->
                        Svar(faktum)
                    }
                }.isSuccess
        ) { "Ikke gyldig generatorsvar $jsonNode" }
    }

    private fun validerPeriode() {
        require(
            jsonNode is ObjectNode && kotlin.runCatching {
                jsonNode["fom"].asLocalDate()
                if (jsonNode.has("tom")) {
                    val tom = jsonNode["tom"]
                    if (!tom.isMissingOrNull()) {
                        tom.asLocalDate()
                    }
                }
            }.isSuccess
        ) { "Ikke gyldig periode svar $jsonNode" }
    }
}
