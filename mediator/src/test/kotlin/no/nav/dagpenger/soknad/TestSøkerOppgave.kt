package no.nav.dagpenger.soknad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import java.util.UUID

internal class TestSøkerOppgave(private val søknadUUID: UUID, private val eier: String, private val json: String) :
    SøkerOppgave {
    override fun søknadUUID(): UUID = søknadUUID

    override fun eier(): String = eier

    override fun toJson(): String = json
    override fun sannsynliggjøringer(): Set<Sannsynliggjøring> = emptySet()
    override fun erFerdig(): Boolean {
        TODO("Not yet implemented")
    }
}

internal fun faktumJson(id: String, beskrivendeId: String, generertAv: String? = null) = jacksonObjectMapper().readTree(
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
    |  "readOnly": false,
    |  "generertAv": "$generertAv"
    |}
    """.trimMargin(),
)
