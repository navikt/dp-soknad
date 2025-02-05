package no.nav.dagpenger.soknad

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import mu.KLogger
import mu.KotlinLogging
import no.nav.dagpenger.soknad.hendelse.Hendelse

class BehovMediator(
    private val rapidsConnection: RapidsConnection,
    private val sikkerLogg: KLogger,
) {

    private companion object {
        val logger = KotlinLogging.logger { }
    }

    internal fun håndter(hendelse: Hendelse) {
        hendelse.kontekster().forEach { if (!it.hasErrors()) håndter(hendelse, it.behov()) }
    }

    private fun håndter(
        hendelse: Hendelse,
        behov: List<Aktivitetslogg.Aktivitet.Behov>,
    ) {
        behov
            .groupBy { it.kontekst() }
            .onEach { (_, behovMap) ->
                require(
                    behovMap.size == behovMap.map { it.type.name }
                        .toSet().size,
                ) { "Kan ikke produsere samme behov på samme kontekst" }
            }
            .forEach { (kontekst, behov) ->
                val behovMap: Map<String, Map<String, Any>> =
                    behov.associate { enkeltBehov -> enkeltBehov.type.name to enkeltBehov.detaljer() }
                val behovParametere =
                    behovMap.values.fold<Map<String, Any>, Map<String, Any>>(emptyMap()) { acc, map -> acc + map }
                val final = erFinal(behovMap.size)
                (kontekst + behovMap + behovParametere).let { JsonMessage.newNeed(behovMap.keys, it + final) }
                    .also { message ->
                        sikkerLogg.info("sender behov for {}:\n{}", behovMap.keys, message.toJson())
                        rapidsConnection.publish(hendelse.ident(), message.toJson())
                        logger.info("Sender behov for {}", behovMap.keys)
                    }
            }
    }

    private fun erFinal(antallBehov: Int) =
        if (antallBehov == 1) {
            mapOf("@final" to true)
        } else {
            emptyMap()
        }
}
