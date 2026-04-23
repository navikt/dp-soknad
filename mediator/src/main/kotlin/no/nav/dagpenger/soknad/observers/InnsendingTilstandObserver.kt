package no.nav.dagpenger.soknad.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.soknad.InnsendingObserver

class InnsendingTilstandObserver(
    private val messageContext: MessageContext,
) : InnsendingObserver {
    @Suppress("MaxLineLength")
    override fun innsendingTilstandEndret(event: InnsendingObserver.InnsendingEndretTilstandEvent) {
        logger.info {
            "Innsending ${event.innsendingId} av type ${event.innsendingType.name} endret tilstand fra " +
                "${event.forrigeTilstand.name} til ${event.gjeldendeTilstand.name}"
        }
        val eventName = "innsending_tilstand_endret"
        messageContext.publish(
            event.ident,
            JsonMessage
                .newMessage(
                    eventName,
                    mapOf(
                        "innsendingId" to event.innsendingId,
                        "innsendingType" to event.innsendingType.name,
                        "gjeldendeTilstand" to event.gjeldendeTilstand.name,
                        "forrigeTilstand" to event.forrigeTilstand.name,
                        "forventetFerdig" to event.forventetFerdig,
                    ),
                ).toJson(),
        )
    }

    private companion object {
        private val logger = KotlinLogging.logger { }
    }
}
