package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.TextNode
import no.nav.dagpenger.quizshow.api.serder.objectMapper

data class Svar(val type: String, val svar: Any) {

    fun validerOgKonverter(): JsonNode {
        return when (type) {
            "boolean" -> validerBoolean(svar)
            "flervalg" -> validerFlervalg(svar)
            "envalg" -> validerTekst(svar)
            "localdate" -> validerLocalDate(svar)
            else -> {
                throw IllegalArgumentException("Kjenner ikke typen $type")
            }
        }
    }

    private fun validerLocalDate(svar: Any): TextNode {
        TODO("not implemented")
    }

    private fun validerTekst(svar: Any): TextNode {
        require(svar is String) { " Svar er ikke tekst " }
        return TextNode(svar)
    }

    private fun validerFlervalg(svar: Any): ArrayNode {
        val valgteSvaralternativer = svar as List<String>
        if (valgteSvaralternativer.isEmpty()) {
            throw IllegalArgumentException("Svar må alltid inneholde ett eller flere svaralternativer")
        }
        val svar = objectMapper.createArrayNode()
        valgteSvaralternativer.forEach { svar.add(it) }
        return svar
    }

    private fun validerBoolean(svar: Any): BooleanNode {
        require(svar is Boolean) { " Svar er ikke boolean " }
        return BooleanNode.valueOf(svar)
    }
}
