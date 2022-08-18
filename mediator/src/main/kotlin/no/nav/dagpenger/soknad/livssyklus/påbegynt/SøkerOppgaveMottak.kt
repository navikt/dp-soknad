package no.nav.dagpenger.soknad.livssyklus.påbegynt

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

interface SøkerOppgave {
    fun søknadUUID(): UUID
    fun eier(): String
    fun ferdig(): Boolean
    fun asFrontendformat(): JsonNode
    fun asJson(): String
    fun sannsynliggjøringer(): Set<Sannsynliggjøring>

    object Keys {
        val SEKSJONER = "seksjoner"
        val SØKNAD_UUID = "søknad_uuid"
        val FØDSELSNUMMER = "fødselsnummer"
        val FERDIG = "ferdig"
    }
}

internal class SøkerOppgaveMottak(
    rapidsConnection: RapidsConnection,
    private val søknadMediator: SøknadMediator
) : River.PacketListener {
    private companion object {
        val logger = KotlinLogging.logger {}
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "søker_oppgave") }
            validate { it.rejectKey("@løsning") }
            validate {
                it.requireKey(
                    SøkerOppgave.Keys.SEKSJONER,
                    SøkerOppgave.Keys.SØKNAD_UUID,
                    SøkerOppgave.Keys.FØDSELSNUMMER,
                    "@opprettet",
                    SøkerOppgave.Keys.FERDIG
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søkerOppgave = SøkerOppgaveMelding(packet)
        withLoggingContext("søknadId" to søkerOppgave.søknadUUID().toString()) {
            logger.info { "Mottatt pakke ${packet["@event_name"].asText()}" }
            søknadMediator.behandle(søkerOppgave)
        }
    }
}

internal class SøkerOppgaveMelding(private val jsonMessage: JsonMessage) : SøkerOppgave {
    override fun søknadUUID(): UUID = UUID.fromString(jsonMessage[SøkerOppgave.Keys.SØKNAD_UUID].asText())
    override fun eier(): String = jsonMessage[SøkerOppgave.Keys.FØDSELSNUMMER].asText()
    override fun ferdig(): Boolean = jsonMessage[SøkerOppgave.Keys.FERDIG].asBoolean()
    override fun asFrontendformat(): JsonNode = objectMapper.readTree(jsonMessage.toJson())
    override fun asJson(): String = jsonMessage.toJson()
    override fun sannsynliggjøringer(): Set<Sannsynliggjøring> {
        val seksjoner = jsonMessage[SøkerOppgave.Keys.SEKSJONER]
        val sannsynliggjøringer = mutableMapOf<String, Sannsynliggjøring>()
        val fakta: List<Faktum> = seksjoner.findValues("fakta").flatMap<JsonNode, Faktum> { fakta ->
            fakta.fold(mutableListOf()) { acc, faktum ->
                when (faktum["type"].asText()) {
                    "generator" -> faktum["svar"].forEach { svarliste ->
                        svarliste.forEach { generertFaktum ->
                            acc.add(grunnleggendeFaktum(generertFaktum))
                        }
                    }
                    else -> acc.add(grunnleggendeFaktum(faktum))
                }
                acc
            }
        }.filter { it.sannsynliggjøresAv.isNotEmpty() }

        fakta.forEach { faktum ->
            faktum.sannsynliggjøresAv.forEach { sannsynliggjøring ->
                sannsynliggjøringer.getOrPut(
                    sannsynliggjøring.id
                ) { Sannsynliggjøring(sannsynliggjøring.id, sannsynliggjøring) }.sannsynliggjør(faktum)
            }
        }

        return sannsynliggjøringer.values.toSet()
    }

    private fun grunnleggendeFaktum(faktum: JsonNode): Faktum = Faktum(
        faktum["id"].asText(),
        faktum["beskrivendeId"].asText(),
        faktum["type"].asText(),
        roller = faktum["roller"].map { it.asText() },
        svar = faktum["svar"],
        sannsynliggjøresAv = faktum["sannsynliggjøresAv"].map { grunnleggendeFaktum(it) }
    )
}
