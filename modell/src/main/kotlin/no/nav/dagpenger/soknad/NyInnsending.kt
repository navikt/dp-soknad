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
    brevkode: Brevkode?,
    private val ettersendinger: MutableList<Ettersending>
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
        ettersendinger = mutableListOf(),
        brevkode = brevkode
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
            brevkode: Brevkode?
        ): NyInnsending {
            val tilstand: Tilstand = when (tilstandsType) {
                TilstandType.Opprettet -> Opprettet
                TilstandType.AvventerBrevkode -> AvventerMetadata
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
                brevkode,
                ettersendinger.toMutableList()
            )
        }
    }

    fun ettersend(hendelse: SøknadInnsendtHendelse, dokumentkrav: Dokumentkrav) {
        Ettersending(
            InnsendingType.ETTERSENDING_TIL_DIALOG,
            hendelse.innsendtidspunkt(),
            dokumentkrav,
            brevkode
        ).also { ettersending ->
            ettersendinger.add(ettersending)
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
