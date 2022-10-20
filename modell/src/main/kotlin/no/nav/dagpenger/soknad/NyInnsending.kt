package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
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
    private val ettersendinger: MutableList<Ettersending>
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
        dokumentkrav: Dokumentkrav,
        metadata: Metadata? = null
    ) : this(
        innsendingId = UUID.randomUUID(),
        type = type,
        innsendt = innsendt,
        journalpostId = null,
        tilstand = Opprettet,
        dokumenter = dokumentkrav.tilDokument(),
        ettersendinger = mutableListOf(),
        metadata = metadata
    )

    override val innsendinger get() = listOf(this) + ettersendinger

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
                ettersendinger.toMutableList()
            )
        }
    }

    override fun addObserver(innsendingObserver: InnsendingObserver) {
        super.addObserver(innsendingObserver)
        ettersendinger.forEach { it.addObserver(innsendingObserver) }
    }

    fun ettersend(hendelse: SøknadInnsendtHendelse, dokumentkrav: Dokumentkrav) {
        Ettersending(
            InnsendingType.ETTERSENDING_TIL_DIALOG,
            hendelse.innsendtidspunkt(),
            dokumentkrav,
            metadata
        ).also { ettersending ->
            ettersendinger.add(ettersending)
            observers.forEach { ettersending.addObserver(it) }
            ettersending.håndter(hendelse)
        }
    }

    override fun accept(visitor: InnsendingVisitor) {
        super.accept(visitor)
        visitor.preVisitEttersendinger()
        ettersendinger.forEach { it.accept(visitor) }
        visitor.postVisitEttersendinger()
    }

    override fun equals(other: Any?): Boolean {
        return other is NyInnsending && super.equals(other) && ettersendinger == other.ettersendinger
    }
}
