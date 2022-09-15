package no.nav.dagpenger.soknad.serder

import com.fasterxml.jackson.databind.JsonNode
import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Krav.Svar
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.serder.SøknadDTO.DokumentDTO.Companion.rehydrer
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID

class SøknadDTO(
    val søknadsId: UUID,
    val ident: String,
    val tilstandType: TilstandDTO,
    var dokumenter: List<DokumentDTO>,
    val journalpostId: String?,
    val innsendtTidspunkt: ZonedDateTime?,
    val språkDTO: SpråkDTO,
    var dokumentkrav: DokumentkravDTO,
    val sistEndretAvBruker: ZonedDateTime?,
    var aktivitetslogg: AktivitetsloggDTO? = null
) {
    fun rehydrer(): Søknad = Søknad.rehydrer(
        søknadId = this.søknadsId,
        ident = this.ident,
        journalpost = this.dokumenter.rehydrer(),
        journalpostId = this.journalpostId,
        innsendtTidspunkt = this.innsendtTidspunkt,
        språk = this.språkDTO.rehydrer(),
        dokumentkrav = this.dokumentkrav.rehydrer(),
        sistEndretAvBruker = this.sistEndretAvBruker,
        tilstandsType = this.tilstandType.rehydrer(),
        aktivitetslogg = aktivitetslogg?.konverterTilAktivitetslogg() ?: Aktivitetslogg()
    )

    class DokumentDTO(
        val urn: String
    ) {
        companion object {
            fun List<DokumentDTO>.rehydrer(): Søknad.Journalpost? {
                return if (this.isEmpty()) null else {
                    Søknad.Journalpost(
                        varianter = this.map {
                            Søknad.Journalpost.Variant(
                                urn = it.urn,
                                format = "ARKIV",
                                type = "PDF"
                            )
                        }
                    )
                }
            }
        }
    }

    class SpråkDTO(val verdi: String) {
        constructor(språk: Locale) : this(språk.toLanguageTag())

        fun rehydrer() = Språk(verdi)
    }

    data class DokumentkravDTO(
        val kravData: Set<KravDTO>
    ) {
        fun rehydrer(): Dokumentkrav = Dokumentkrav.rehydrer(
            krav = kravData.map { it.rehydrer() }.toSet()
        )

        data class SannsynliggjøringDTO(
            val id: String,
            val faktum: JsonNode,
            val sannsynliggjør: Set<JsonNode>
        ) {
            fun rehydrer() = Sannsynliggjøring(
                id = this.id,
                faktum = Faktum(this.faktum),
                sannsynliggjør = this.sannsynliggjør.map { Faktum(it) }.toMutableSet()
            )
        }

        data class SvarDTO(
            val begrunnelse: String?,
            val filer: Set<KravDTO.FilDTO>,
            val valg: SvarValgDTO
        ) {
            fun rehydrer() = Svar(
                filer = this.filer.map { it.rehydrer() }.toMutableSet(),
                valg = when (this.valg) {
                    SvarValgDTO.IKKE_BESVART -> Svar.SvarValg.IKKE_BESVART
                    SvarValgDTO.SEND_NÅ -> Svar.SvarValg.SEND_NÅ
                    SvarValgDTO.SEND_SENERE -> Svar.SvarValg.SEND_SENERE
                    SvarValgDTO.ANDRE_SENDER -> Svar.SvarValg.ANDRE_SENDER
                    SvarValgDTO.SEND_TIDLIGERE -> Svar.SvarValg.SEND_TIDLIGERE
                    SvarValgDTO.SENDER_IKKE -> Svar.SvarValg.SENDER_IKKE
                },
                begrunnelse = this.begrunnelse,
                bundle = null
            )

            enum class SvarValgDTO {
                IKKE_BESVART,
                SEND_NÅ,
                SEND_SENERE,
                ANDRE_SENDER,
                SEND_TIDLIGERE,
                SENDER_IKKE;
            }
        }

        data class KravDTO(
            val id: String,
            val beskrivendeId: String,
            val sannsynliggjøring: SannsynliggjøringDTO,
            val tilstand: KravTilstandDTO,
            val svar: SvarDTO
        ) {
            fun rehydrer() = Krav(
                id = this.id,
                svar = this.svar.rehydrer(),
                sannsynliggjøring = this.sannsynliggjøring.rehydrer(),
                tilstand = when (this.tilstand) {
                    KravTilstandDTO.AKTIV -> Krav.KravTilstand.AKTIV
                    KravTilstandDTO.INAKTIV -> Krav.KravTilstand.INAKTIV
                }
            )

            enum class KravTilstandDTO {
                AKTIV,
                INAKTIV
            }

            data class FilDTO(
                val filnavn: String,
                val urn: URN,
                val storrelse: Long,
                val tidspunkt: ZonedDateTime
            ) {
                fun rehydrer() = Krav.Fil(
                    filnavn = this.filnavn,
                    urn = this.urn,
                    storrelse = this.storrelse,
                    tidspunkt = this.tidspunkt
                )
            }
        }
    }

    enum class TilstandDTO {
        UnderOpprettelse,
        Påbegynt,
        AvventerArkiverbarSøknad,
        AvventerMidlertidligJournalføring,
        AvventerJournalføring,
        Journalført,
        Slettet;

        fun rehydrer(): Søknad.Tilstand.Type = when (this) {
            UnderOpprettelse -> Søknad.Tilstand.Type.UnderOpprettelse
            Påbegynt -> Søknad.Tilstand.Type.Påbegynt
            AvventerArkiverbarSøknad -> Søknad.Tilstand.Type.AvventerArkiverbarSøknad
            AvventerMidlertidligJournalføring -> Søknad.Tilstand.Type.AvventerMidlertidligJournalføring
            AvventerJournalføring -> Søknad.Tilstand.Type.AvventerJournalføring
            Journalført -> Søknad.Tilstand.Type.Journalført
            Slettet -> Søknad.Tilstand.Type.Slettet
        }

        companion object {
            fun rehydrer(dbTilstand: String): TilstandDTO {
                return TilstandDTO.valueOf(dbTilstand)
            }
        }
    }
}
