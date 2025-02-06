package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.fasterxml.jackson.databind.JsonNode
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.SøknadData
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import java.io.InputStream
import java.util.UUID

interface SøkerOppgave : SøknadData {
    fun søknadUUID(): UUID

    fun eier(): String

    fun sannsynliggjøringer(): Set<Sannsynliggjøring>

    object Keys {
        val SEKSJONER = "seksjoner"
        val SØKNAD_UUID = "søknad_uuid"
        val FØDSELSNUMMER = "fødselsnummer"
        val FERDIG = "ferdig"
    }

    fun hendelse(): SøkeroppgaveHendelse = SøkeroppgaveHendelse(søknadUUID(), eier(), sannsynliggjøringer())
}

internal open class SøkerOppgaveMelding(private val jsonMessage: JsonNode) : SøkerOppgave {
    constructor(message: String) : this(objectMapper.readTree(message))
    constructor(message: InputStream) : this(objectMapper.readTree(message))

    override fun søknadUUID(): UUID = UUID.fromString(jsonMessage[SøkerOppgave.Keys.SØKNAD_UUID].asText())

    override fun eier(): String = jsonMessage[SøkerOppgave.Keys.FØDSELSNUMMER].asText()

    override fun toJson(): String = jsonMessage.toString()

    override fun sannsynliggjøringer(): Set<Sannsynliggjøring> {
        val seksjoner = jsonMessage[SøkerOppgave.Keys.SEKSJONER]
        val sannsynliggjøringer = mutableMapOf<String, Sannsynliggjøring>()
        val fakta: List<Faktum> =
            seksjoner.findValues("fakta").flatMap<JsonNode, Faktum> { fakta ->
                fakta.fold(mutableListOf()) { acc, faktum ->
                    when (faktum["type"].asText()) {
                        "generator" ->
                            faktum["svar"]?.forEach { svarliste ->
                                svarliste.forEach { generertFaktum ->
                                    acc.add(grunnleggendeFaktum(generertFaktum))
                                }
                            }

                        else -> acc.add(grunnleggendeFaktum(faktum))
                    }
                    acc
                }
            }.filter {
                it.sannsynliggjøresAv.isNotEmpty()
            }

        fakta.forEach { faktum ->
            faktum.sannsynliggjøresAv.forEach { sannsynliggjøring ->
                sannsynliggjøringer.getOrPut(
                    sannsynliggjøring.id,
                ) { Sannsynliggjøring(sannsynliggjøring.id, sannsynliggjøring) }.sannsynliggjør(faktum)
            }
        }

        return sannsynliggjøringer.values.toSet()
    }

    override fun erFerdig() = jsonMessage["ferdig"].asBoolean()

    private fun grunnleggendeFaktum(faktum: JsonNode): Faktum = Faktum(faktum)
}
