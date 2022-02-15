package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.TextNode
import no.nav.helse.rapids_rivers.asLocalDate

class ApiSvar(json: JsonNode) {

    val svar: JsonNode
    val type: String
    init {
        require(json.has("type")) { " Ingen type definert, ugyldig faktumsvar" }
        require(json.has("svar")) { "Ingen svar definert, ugyldig faktumsvar" }
        svar = json["svar"]
        type = json["type"].asText()
        validerType()
    }

    private fun validerType() {
        when (type) {
            "boolean" -> require(svar is BooleanNode) { "Ikke gyldig boolean svar $svar" }
            "flervalg" -> require(svar is ArrayNode && svar.all { it.isTextual }) { "Ikke gyldig flervalg svar $svar" }
            "envalg" -> require(svar is TextNode) { "Ikke gyldig envalg svar $svar" }
            "localdate" -> require(svar is TextNode && kotlin.runCatching { svar.asLocalDate() }.isSuccess) { "Ikke gyldig localdate svar $svar" }
            "double" -> require(svar is NumericNode && kotlin.runCatching { svar.asDouble() }.isSuccess) { "Ikke gyldig double svar $svar" }
            else -> {
                throw IllegalArgumentException("Kjenner ikke typen $type")
            }
        }
    }
}