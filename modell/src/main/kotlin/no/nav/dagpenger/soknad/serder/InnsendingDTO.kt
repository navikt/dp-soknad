package no.nav.dagpenger.soknad.serder

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
    var hovedDokument: DokumentDTO? = null,
    val dokumenter: List<DokumentDTO>,
    val brevkode: BrevkodeDTO?

) {
    fun rehydrer(): NyInnsending {
        return NyInnsending.rehydrer(
            innsendingId = this.innsendingId,
            type = this.type.rehydrer(),
            innsendt = this.innsendt,
            journalpostId = this.journalpostId,
            tilstandsType = this.tilstand.rehydrer(),
            hovedDokument = null,
            dokumenter = listOf(),
            ettersendinger = mutableListOf(),
            brevkode = null
        )
    }

    enum class TilstandDTO {
        Opprettet,
        AvventerBrevkode,
        AvventerArkiverbarSøknad,
        AvventerMidlertidligJournalføring,
        AvventerJournalføring,
        Journalført;

        fun rehydrer(): Innsending.Tilstand.Type {
            return when (this) {
                Opprettet -> Innsending.Tilstand.Type.Opprettet
                AvventerBrevkode -> Innsending.Tilstand.Type.AvventerBrevkode
                AvventerArkiverbarSøknad -> Innsending.Tilstand.Type.AvventerArkiverbarSøknad
                AvventerMidlertidligJournalføring -> Innsending.Tilstand.Type.AvventerMidlertidligJournalføring
                AvventerJournalføring -> Innsending.Tilstand.Type.AvventerBrevkode
                Journalført -> Innsending.Tilstand.Type.Journalført
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

    class DokumentDTO

    class BrevkodeDTO
}
