package no.nav.dagpenger.soknad.søknad

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.isMissingOrNull

class Svar(json: JsonNode) {

    val svarAsJson: JsonNode
    val type: String

    init {
        require(json.has("type")) { " Ingen 'type' definert, ugyldig faktumsvar" }
        require(json.has("svar")) { "Ingen 'svar' definert, ugyldig faktumsvar" }
        svarAsJson = json["svar"]
        type = json["type"].asText()
        validerType()
    }

    private fun validerType() {
        when (type) {
            "boolean" -> require(svarAsJson.isBoolean, feilmelding())
            "flervalg" -> require(
                erValg() && svarAsJson.size() > 0,
                feilmelding()
            )
            "envalg" -> require((erTekst()), feilmelding())
            "localdate" -> require(
                svarAsJson.isTextual && kotlin.runCatching { svarAsJson.asLocalDate() }.isSuccess,
                feilmelding()
            )
            "double" -> require(svarAsJson.isDouble || svarAsJson.isNumber, feilmelding())
            "int" -> require(svarAsJson.isInt, feilmelding())
            "tekst" -> require(erTekst(), feilmelding())
            "land" -> require(svarAsJson.isTextual && svarAsJson.asText().length < 4, feilmelding())
            "periode" -> validerPeriode()
            "generator" -> validerGenerator()
            else -> {
                throw IllegalArgumentException("Kjenner ikke typen $type")
            }
        }
    }

    private fun erTekst() = svarAsJson.isTextual && svarAsJson.asText().isNotBlank()

    private fun erValg() = svarAsJson.isArray && svarAsJson.all { it.asText().isNotBlank() }

    private fun feilmelding(): () -> String = { "Ikke gyldig '$type' svar: '$svarAsJson'" }

    private fun validerGenerator() {
        require(svarAsJson is ArrayNode) { feilmelding() }
        kotlin.runCatching {
            svarAsJson.forEach { indeks ->
                indeks.forEach { faktum ->
                    require(faktum.has("id")) { "id må alltid angis på generator svar, mangler i $faktum" }
                    if (faktum["id"].asText().contains(".")) {
                        val id = faktum["id"].asText()
                        val idUtenIndeks = id.substring(0, id.indexOf("."))
                        (faktum as ObjectNode).put("id", idUtenIndeks)
                    }
                    Svar(faktum)
                }
            }
        }.fold(
            {},
            {
                throw IllegalArgumentException("Ikke gyldig '$type', feil i underliggende faktum ${it.message}, svar: $svarAsJson")
            }
        )
    }

    private fun validerPeriode() {
        require(
            svarAsJson is ObjectNode && kotlin.runCatching {
                svarAsJson["fom"].asLocalDate()
                if (svarAsJson.has("tom")) {
                    val tom = svarAsJson["tom"]
                    if (!tom.isMissingOrNull()) {
                        tom.asLocalDate()
                    }
                }
            }.isSuccess,
            feilmelding()
        )
    }
}
