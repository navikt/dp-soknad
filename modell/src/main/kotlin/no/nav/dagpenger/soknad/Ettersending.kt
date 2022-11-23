package no.nav.dagpenger.soknad

import java.time.ZonedDateTime
import java.util.UUID

class Ettersending private constructor(
    innsendingId: UUID,
    type: InnsendingType,
    innsendt: ZonedDateTime,
    journalpostId: String?,
    tilstand: Tilstand,
    hovedDokument: Dokument? = null,
    dokumenter: List<Dokument>,
    metadata: Metadata?
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

    companion object {
        fun rehydrer(
            innsendingId: UUID,
            type: InnsendingType,
            innsendt: ZonedDateTime,
            journalpostId: String?,
            tilstandsType: TilstandType,
            hovedDokument: Dokument? = null,
            dokumenter: List<Dokument>,
            metadata: Metadata?
        ): Ettersending {
            val tilstand: Tilstand = when (tilstandsType) {
                TilstandType.Opprettet -> Opprettet
                TilstandType.AvventerMetadata -> AvventerMetadata
                TilstandType.AvventerArkiverbarSøknad -> AvventerArkiverbarSøknad
                TilstandType.AvventerMidlertidligJournalføring -> AvventerMidlertidligJournalføring
                TilstandType.AvventerJournalføring -> AvventerJournalføring
                TilstandType.Journalført -> Journalført
            }
            return Ettersending(
                innsendingId,
                type,
                innsendt,
                journalpostId,
                tilstand,
                hovedDokument,
                dokumenter,
                metadata
            )
        }
    }
}
