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
            tilstandsType: Tilstand.Type,
            hovedDokument: Dokument? = null,
            dokumenter: List<Dokument>,
            brevkode: Brevkode?
        ): Innsending {
            val tilstand: Tilstand = when (tilstandsType) {
                Tilstand.Type.Opprettet -> Opprettet
                Tilstand.Type.AvventerBrevkode -> AvventerMetadata
                Tilstand.Type.AvventerArkiverbarSøknad -> AvventerArkiverbarSøknad
                Tilstand.Type.AvventerMidlertidligJournalføring -> AvventerMidlertidligJournalføring
                Tilstand.Type.AvventerJournalføring -> AvventerJournalføring
                Tilstand.Type.Journalført -> Journalført
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
}
