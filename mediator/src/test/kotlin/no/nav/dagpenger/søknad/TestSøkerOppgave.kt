package no.nav.dagpenger.søknad

import com.fasterxml.jackson.databind.JsonNode
import no.nav.dagpenger.søknad.livssyklus.påbegynt.SøkerOppgave
import java.util.UUID

internal class TestSøkerOppgave(private val søknadUUID: UUID, private val eier: String, private val json: String) :
    SøkerOppgave {
    override fun søknadUUID(): UUID = søknadUUID

    override fun eier(): String = eier

    override fun ferdig(): Boolean {
        TODO("not implemented")
    }

    override fun asFrontendformat(): JsonNode {
        TODO("not implemented")
    }

    override fun asJson(): String = json
}
