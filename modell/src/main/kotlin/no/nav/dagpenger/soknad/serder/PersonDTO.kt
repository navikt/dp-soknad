package no.nav.dagpenger.soknad.serder

import com.fasterxml.jackson.databind.JsonNode
import de.slub.urn.URN
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Faktum
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.SpesifikkKontekst
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknadhåndterer
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.DokumentDTO.Companion.rehydrer
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.DokumentkravDTO.KravDTO.FilDTO.Companion.toFilData
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.DokumentkravDTO.SannsynliggjøringDTO.Companion.toSannsynliggjøringData
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.DokumentkravDTO.SvarDTO.Companion.tilSvarData
import no.nav.dagpenger.soknad.serder.PersonDTO.SøknadDTO.DokumentkravDTO.SvarDTO.SvarValgDTO.Companion.tilSvarValgDTO
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID
class PersonDTO( // TODO: Verken Person eller Søknadhåndterer skal være rotaggregat
    val ident: String,
    var søknader: List<SøknadDTO> = emptyList(),
    var aktivitetsLogg: AktivitetsloggDTO? = null
) {
    fun createSøknadhåndterer(): Søknadhåndterer {
        return Søknadhåndterer.rehydrer(
            aktivitetslogg = this.aktivitetsLogg?.konverterTilAktivitetslogg() ?: Aktivitetslogg(),
            søknadsfunksjon = { søknadhåndterer: Søknadhåndterer ->
                søknader.map { it: SøknadDTO ->
                    Søknad.rehydrer(
                        søknadId = it.søknadsId,
                        søknadObserver = søknadhåndterer,
                        ident = ident,
                        dokument = it.dokumenter.rehydrer(),
                        journalpostId = it.journalpostId,
                        innsendtTidspunkt = it.innsendtTidspunkt,
                        språk = it.språkDTO.rehydrer(),
                        dokumentkrav = it.dokumentkrav.rehydrer(),
                        sistEndretAvBruker = it.sistEndretAvBruker,
                        tilstandsType = it.tilstandType.rehydrer()
                    )
                }.toMutableList()
            },
        )
    }

    class SøknadDTO(
        val søknadsId: UUID,
        val ident: String,
        val tilstandType: TilstandDTO,
        var dokumenter: List<DokumentDTO>,
        val journalpostId: String?,
        val innsendtTidspunkt: ZonedDateTime?,
        val språkDTO: SpråkDTO,
        var dokumentkrav: DokumentkravDTO,
        val sistEndretAvBruker: ZonedDateTime?
    ) {

        fun rehydrer(): Søknad = Søknad.rehydrer(
            søknadId = this.søknadsId,
            søknadObserver = Søknadhåndterer(),
            ident = this.ident,
            dokument = this.dokumenter.rehydrer(),
            journalpostId = this.journalpostId,
            innsendtTidspunkt = this.innsendtTidspunkt,
            språk = this.språkDTO.rehydrer(),
            dokumentkrav = this.dokumentkrav.rehydrer(),
            sistEndretAvBruker = this.sistEndretAvBruker,
            tilstandsType = this.tilstandType.rehydrer()

        )
        class DokumentDTO(
            val urn: String
        ) {
            companion object {
                fun List<DokumentDTO>.rehydrer(): Søknad.Dokument? {
                    return if (this.isEmpty()) null else {
                        Søknad.Dokument(
                            varianter = this.map {
                                Søknad.Dokument.Variant(
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

                companion object {
                    fun Sannsynliggjøring.toSannsynliggjøringData() = SannsynliggjøringDTO(
                        id = this.id,
                        faktum = this.faktum().json,
                        sannsynliggjør = this.sannsynliggjør().map { it.json }.toSet()
                    )
                }
            }

            data class SvarDTO(
                val begrunnelse: String?,
                val filer: Set<KravDTO.FilDTO>,
                val valg: SvarValgDTO
            ) {

                companion object {

                    fun Krav.Svar.tilSvarData(): SvarDTO {
                        return SvarDTO(
                            begrunnelse = this.begrunnelse,
                            filer = this.filer.map { it.toFilData() }.toSet(),
                            valg = this.valg.tilSvarValgDTO()
                        )
                    }
                }

                fun rehydrer(): Krav.Svar {
                    return Krav.Svar(
                        filer = this.filer.map { it.rehydrer() }.toMutableSet(),
                        valg = when (this.valg) {
                            SvarValgDTO.IKKE_BESVART -> Krav.Svar.SvarValg.IKKE_BESVART
                            SvarValgDTO.SEND_NÅ -> Krav.Svar.SvarValg.SEND_NÅ
                            SvarValgDTO.SEND_SENERE -> Krav.Svar.SvarValg.SEND_SENERE
                            SvarValgDTO.ANDRE_SENDER -> Krav.Svar.SvarValg.ANDRE_SENDER
                            SvarValgDTO.SEND_TIDLIGERE -> Krav.Svar.SvarValg.SEND_TIDLIGERE
                            SvarValgDTO.SENDER_IKKE -> Krav.Svar.SvarValg.SENDER_IKKE
                        },
                        begrunnelse = this.begrunnelse
                    )
                }

                enum class SvarValgDTO {
                    IKKE_BESVART,
                    SEND_NÅ,
                    SEND_SENERE,
                    ANDRE_SENDER,
                    SEND_TIDLIGERE,
                    SENDER_IKKE;

                    companion object {
                        fun Krav.Svar.SvarValg.tilSvarValgDTO() = when (this) {
                            Krav.Svar.SvarValg.IKKE_BESVART -> IKKE_BESVART
                            Krav.Svar.SvarValg.SEND_NÅ -> SEND_NÅ
                            Krav.Svar.SvarValg.SEND_SENERE -> SEND_SENERE
                            Krav.Svar.SvarValg.ANDRE_SENDER -> ANDRE_SENDER
                            Krav.Svar.SvarValg.SEND_TIDLIGERE -> SEND_TIDLIGERE
                            Krav.Svar.SvarValg.SENDER_IKKE -> SENDER_IKKE
                        }
                    }
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
                    },
                )

                companion object {
                    fun Krav.toKravdata() = KravDTO(
                        id = this.id,
                        beskrivendeId = this.beskrivendeId,
                        svar = this.svar.tilSvarData(),
                        sannsynliggjøring = this.sannsynliggjøring.toSannsynliggjøringData(),
                        tilstand = when (this.tilstand) {
                            Krav.KravTilstand.AKTIV -> KravTilstandDTO.AKTIV
                            Krav.KravTilstand.INAKTIV -> KravTilstandDTO.INAKTIV
                        }
                    )
                }

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

                    companion object {
                        fun Krav.Fil.toFilData() = FilDTO(
                            filnavn = this.filnavn,
                            urn = this.urn,
                            storrelse = this.storrelse,
                            tidspunkt = this.tidspunkt
                        )
                    }

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

    data class AktivitetsloggDTO(
        val aktiviteter: List<AktivitetDTO>
    ) {
        data class AktivitetDTO(
            val alvorlighetsgrad: Alvorlighetsgrad,
            val label: Char,
            val behovtype: String?,
            val melding: String,
            val tidsstempel: String,
            val kontekster: List<SpesifikkKontekstDTO>,
            val detaljer: Map<String, Any>
        )

        data class SpesifikkKontekstDTO(
            val kontekstType: String,
            val kontekstMap: Map<String, String>
        )

        enum class Alvorlighetsgrad {
            INFO,
            WARN,
            BEHOV,
            ERROR,
            SEVERE
        }

        fun konverterTilAktivitetslogg(): Aktivitetslogg = konverterTilAktivitetslogg(this)

        private fun konverterTilAktivitetslogg(aktivitetsloggDTO: AktivitetsloggDTO): Aktivitetslogg {
            val aktiviteter = mutableListOf<Aktivitetslogg.Aktivitet>()
            aktivitetsloggDTO.aktiviteter.forEach {
                val kontekster = it.kontekster.map { spesifikkKontekstData ->
                    SpesifikkKontekst(
                        spesifikkKontekstData.kontekstType,
                        spesifikkKontekstData.kontekstMap
                    )
                }
                aktiviteter.add(
                    when (it.alvorlighetsgrad) {
                        Alvorlighetsgrad.INFO -> Aktivitetslogg.Aktivitet.Info(
                            kontekster,
                            it.melding,
                            it.tidsstempel
                        )

                        Alvorlighetsgrad.WARN -> Aktivitetslogg.Aktivitet.Warn(
                            kontekster,
                            it.melding,
                            it.tidsstempel
                        )

                        Alvorlighetsgrad.BEHOV -> Aktivitetslogg.Aktivitet.Behov(
                            Aktivitetslogg.Aktivitet.Behov.Behovtype.valueOf(it.behovtype!!),
                            kontekster,
                            it.melding,
                            it.detaljer,
                            it.tidsstempel
                        )

                        Alvorlighetsgrad.ERROR -> Aktivitetslogg.Aktivitet.Error(
                            kontekster,
                            it.melding,
                            it.tidsstempel
                        )

                        Alvorlighetsgrad.SEVERE -> Aktivitetslogg.Aktivitet.Severe(
                            kontekster,
                            it.melding,
                            it.tidsstempel
                        )
                    }
                )
            }
            return Aktivitetslogg.rehyder(
                aktiviteter = aktiviteter
            )
        }
    }
}
