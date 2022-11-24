package no.nav.dagpenger.soknad

import java.time.ZonedDateTime
import java.util.UUID

class NyInnsending private constructor(
    innsendingId: UUID,
    type: InnsendingType,
    innsendt: ZonedDateTime,
    journalpostId: String?,
    tilstand: Tilstand,
    hovedDokument: Dokument? = null,
    dokumenter: List<Dokument>,
    metadata: Metadata?,
) : Innsending(
    innsendingId,
    type,
    innsendt,
    journalpostId,
    tilstand,
    hovedDokument,
    dokumenter,
    metadata
) {
    internal constructor(
        type: InnsendingType,
        innsendt: ZonedDateTime,
        dokumentkrav: List<Dokument>,
        metadata: Metadata? = null
    ) : this(
        innsendingId = UUID.randomUUID(),
        type = type,
        innsendt = innsendt,
        journalpostId = null,
        tilstand = Opprettet,
        dokumenter = dokumentkrav,
        metadata = metadata
    )

    override val innsendinger get() = listOf(this)

    companion object {
        fun rehydrer(
            innsendingId: UUID,
            type: InnsendingType,
            innsendt: ZonedDateTime,
            journalpostId: String?,
            tilstandsType: TilstandType,
            hovedDokument: Dokument? = null,
            dokumenter: List<Dokument>,
            ettersendinger: List<Ettersending>,
            metadata: Metadata?
        ): NyInnsending {
            val tilstand: Tilstand = when (tilstandsType) {
                TilstandType.Opprettet -> Opprettet
                TilstandType.AvventerMetadata -> AvventerMetadata
                TilstandType.AvventerArkiverbarSøknad -> AvventerArkiverbarSøknad
                TilstandType.AvventerMidlertidligJournalføring -> AvventerMidlertidligJournalføring
                TilstandType.AvventerJournalføring -> AvventerJournalføring
                TilstandType.Journalført -> Journalført
            }
            return NyInnsending(
                innsendingId,
                type,
                innsendt,
                journalpostId,
                tilstand,
                hovedDokument,
                dokumenter,
                metadata,
            )
        }
    }

    override fun addObserver(innsendingObserver: InnsendingObserver) {
        super.addObserver(innsendingObserver)
    }

    override fun accept(visitor: InnsendingVisitor) {
        super.accept(visitor)
        visitor.preVisitEttersendinger()
        visitor.postVisitEttersendinger()
    }

    override fun equals(other: Any?): Boolean {
        return other is NyInnsending && super.equals(other)
    }
}
