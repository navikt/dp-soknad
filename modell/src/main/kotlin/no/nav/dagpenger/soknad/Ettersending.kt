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
    brevkode: Brevkode?
) : Innsending(
    innsendingId,
    type,
    innsendt,
    journalpostId,
    tilstand,
    hovedDokument,
    dokumenter,
    brevkode
) {
    internal constructor(
        type: InnsendingType,
        innsendt: ZonedDateTime,
        dokumentkrav: Dokumentkrav,
        brevkode: Brevkode? = null
    ) : this(
        innsendingId = UUID.randomUUID(),
        type = type,
        innsendt = innsendt,
        journalpostId = null,
        tilstand = Opprettet,
        dokumenter = dokumentkrav.tilDokument(),
        brevkode = brevkode
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
            brevkode: Brevkode?
        ): Ettersending {
            val tilstand: Tilstand = when (tilstandsType) {
                TilstandType.Opprettet -> Opprettet
                TilstandType.AvventerBrevkode -> AvventerMetadata
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
                brevkode
            )
        }
    }

    override fun toString(): String {
        return "Ettersending: ${super.toString()}"
    }
}
