package no.nav.dagpenger.soknad

import java.time.LocalDateTime
import java.util.UUID

interface InnsendingObserver {
    fun innsendingTilstandEndret(event: InnsendingEndretTilstandEvent) {}

    data class InnsendingEndretTilstandEvent(
        val ident: String,
        val innsendingId: UUID,
        val innsendingType: Innsending.InnsendingType,
        val gjeldendeTilstand: Innsending.TilstandType,
        val forrigeTilstand: Innsending.TilstandType,
        val forventetFerdig: LocalDateTime,
    )
}
