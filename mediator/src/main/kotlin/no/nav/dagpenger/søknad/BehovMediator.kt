package no.nav.dagpenger.søknad

import mu.KLogger
import mu.KotlinLogging
import no.nav.dagpenger.søknad.hendelse.Hendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection

class BehovMediator(
    private val rapidsConnection: RapidsConnection,
    private val sikkerLogg: KLogger
) {

    private companion object {
        val logger = KotlinLogging.logger { }
    }

    internal fun håndter(hendelse: Hendelse) {
        hendelse.kontekster().forEach { if (!it.hasErrors()) håndter(hendelse, it.behov()) }
    }

    private fun håndter(
        hendelse: Hendelse,
        behov: List<Aktivitetslogg.Aktivitet.Behov>
    ) {
        behov
            .groupBy { it.kontekst() }
            .onEach { (_, behovMap) ->
                require(
                    behovMap.size == behovMap.map { it.type.name }
                        .toSet().size
                ) { "Kan ikke produsere samme behov på samme kontekst" }
            }
            .forEach { (kontekst, liste) ->
                val behovMap: Map<String, Map<String, Any>> = liste.associate { behov -> behov.type.name to behov.detaljer() }
                val behovParametere = behovMap.values.fold<Map<String, Any>, Map<String, Any>>(emptyMap()) { acc, map -> acc + map }
                (kontekst + behovMap + behovParametere).let { JsonMessage.newNeed(behovMap.keys, it) }
                    .also { message ->
                        sikkerLogg.info("sender behov for {}:\n{}", behovMap.keys, message.toJson())
                        rapidsConnection.publish(hendelse.ident(), message.toJson())
                        logger.info("Sender behov for {}", behovMap.keys)
                    }
            }
    }
}
