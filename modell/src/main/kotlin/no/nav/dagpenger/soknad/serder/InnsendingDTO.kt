package no.nav.dagpenger.soknad.serder

import no.nav.dagpenger.soknad.Ettersending
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.NyInnsending
import java.time.ZonedDateTime
import java.util.UUID

data class InnsendingDTO(
    val innsendingId: UUID,
    val type: InnsendingTypeDTO,
    val innsendt: ZonedDateTime,
    var journalpostId: String?,
    var tilstand: TilstandDTO,
    var hovedDokument: Innsending.Dokument? = null,
    val dokumenter: List<Innsending.Dokument>,
    val brevkode: Innsending.Brevkode?,
    val ettersendinger: List<InnsendingDTO>
) {
    fun rehydrer(): NyInnsending {
        return NyInnsending.rehydrer(
            innsendingId = this.innsendingId,
            type = this.type.rehydrer(),
            innsendt = this.innsendt,
            journalpostId = this.journalpostId,
            tilstandsType = this.tilstand.rehydrer(),
            hovedDokument = hovedDokument,
            dokumenter = dokumenter,
            ettersendinger = ettersendinger.map {
                Ettersending.rehydrer(
                    it.innsendingId,
                    it.type.rehydrer(),
                    it.innsendt,
                    it.journalpostId,
                    it.tilstand.rehydrer(),
                    it.hovedDokument,
                    it.dokumenter,
                    it.brevkode
                )
            },
            brevkode = brevkode
        )
    }

    enum class TilstandDTO {
        Opprettet,
        AvventerBrevkode,
        AvventerArkiverbarSøknad,
        AvventerMidlertidligJournalføring,
        AvventerJournalføring,
        Journalført;

        fun rehydrer(): Innsending.TilstandType {
            return when (this) {
                Opprettet -> Innsending.TilstandType.Opprettet
                AvventerBrevkode -> Innsending.TilstandType.AvventerBrevkode
                AvventerArkiverbarSøknad -> Innsending.TilstandType.AvventerArkiverbarSøknad
                AvventerMidlertidligJournalføring -> Innsending.TilstandType.AvventerMidlertidligJournalføring
                AvventerJournalføring -> Innsending.TilstandType.AvventerJournalføring
                Journalført -> Innsending.TilstandType.Journalført
            }
        }

        companion object {
            fun rehydrer(tilstand: String) = valueOf(tilstand)
        }
    }

    enum class InnsendingTypeDTO {
        NY_DIALOG,
        ETTERSENDING_TIL_DIALOG;

        fun rehydrer(): Innsending.InnsendingType {
            return when (this) {
                NY_DIALOG -> Innsending.InnsendingType.NY_DIALOG
                ETTERSENDING_TIL_DIALOG -> Innsending.InnsendingType.ETTERSENDING_TIL_DIALOG
            }
        }

        companion object {
            fun rehydrer(type: String): InnsendingTypeDTO = valueOf(type)
        }
    }

    data class DokumenterDTO(
        var hovedDokument: Innsending.Dokument? = null,
        val dokumenter: MutableList<Innsending.Dokument> = mutableListOf()
    )
}
