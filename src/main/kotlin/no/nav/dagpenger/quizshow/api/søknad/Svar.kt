package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode

data class Svar(val type: String, val svar: JsonNode) {
    fun valider() {
        if (type == "flervalg") {
            val valgteSvaralternativer = svar as ArrayNode
            if (valgteSvaralternativer.isEmpty()) {
                throw IllegalArgumentException("Svar må alltid inneholde ett eller flere svaralternativer")
            }
        }
    }
}
