package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import java.time.ZonedDateTime
import java.util.UUID

class NyInnsending internal constructor(
    innsendingId: UUID,
    type: InnsendingType,
    innsendt: ZonedDateTime,
    journalpostId: String?,
    tilstand: Tilstand,
    hovedDokument: Dokument? = null,
    dokumenter: List<Dokument>,
    brevkode: Brevkode?,
    private val ettersendinger: MutableList<Ettersending>,
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
}
