package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import de.slub.urn.URN
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.isMissingOrNull
import java.time.LocalDateTime

class GyldigSvar(json: JsonNode) {

    val svarAsJson: JsonNode
    val type: String

    init {
        require(json.has("type")) { "Ingen 'type' definert, ugyldig faktumsvar" }
        require(json.has("svar")) { "Ingen 'svar' definert, ugyldig faktumsvar" }
        svarAsJson = json["svar"]
        type = json["type"].asText()
        validerType()
    }

    private fun validerType() {
        if (svarAsJson.isNull) return
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
            "dokument" -> {
                validerDokument()
            }

            else -> {
                throw IllegalArgumentException("Kjenner ikke typen $type")
            }
        }
    }

    private fun validerDokument() {
        kotlin.runCatching {
            svarAsJson["urn"].asText().let { URN.rfc8141().parse(it) }
            svarAsJson["lastOppTidsstempel"].asText().let { LocalDateTime.parse(it) }
        }.onFailure {
            throw IllegalArgumentException("Ikke gyldig '$type', feil i underliggende faktum ${it.message}, svar: $svarAsJson")
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
                    GyldigSvar(faktum)
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
                val fom = svarAsJson["fom"].asLocalDate()
                if (svarAsJson.has("tom")) {
                    val tomJsonNode = svarAsJson["tom"]
                    if (!tomJsonNode.isMissingOrNull()) {
                        val tom = tomJsonNode.asLocalDate()
                        require(fom <= tom) { "'fom' fra-og-med-dato må være før 'tom' til-og-med-dato " }
                    }
                }
            }.isSuccess,
            feilmelding()
        )
    }
}
