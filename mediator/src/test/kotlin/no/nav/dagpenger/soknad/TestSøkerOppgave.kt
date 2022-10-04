package no.nav.dagpenger.soknad

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import java.time.LocalDateTime
import java.util.UUID

internal class TestSøkerOppgave(private val søknadUUID: UUID, private val eier: String, private val json: String) :
    SøkerOppgave {
    override fun søknadUUID(): UUID = søknadUUID

    override fun eier(): String = eier
    override fun opprettet(): LocalDateTime = LocalDateTime.now()

    override fun ferdig(): Boolean {
        TODO("not implemented")
    }

    override fun asFrontendformat(): JsonNode {
        TODO("not implemented")
    }

    override fun asJson(): String = json
    override fun sannsynliggjøringer(): Set<Sannsynliggjøring> = emptySet()
}
internal fun faktumJson(id: String, beskrivendeId: String) = jacksonObjectMapper().readTree(
    """{
    |  "id": "$id",
    |  "type": "boolean",
    |  "beskrivendeId": "$beskrivendeId",
    |  "svar": true,
    |  "roller": [
    |    "søker"
    |  ],
    |  "gyldigeValg": [
    |    "f1.svar.ja",
    |    "f1.svar.nei"
    |  ],
    |  "sannsynliggjoresAv": [],
    |  "readOnly": false
    |}
    """.trimMargin()
)
