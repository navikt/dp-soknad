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
import no.nav.dagpenger.soknad.serder.PersonData.SøknadData.DokumentData.Companion.rehydrer
import no.nav.dagpenger.soknad.serder.PersonData.SøknadData.DokumentkravData.KravData.FilData.Companion.toFilData
import no.nav.dagpenger.soknad.serder.PersonData.SøknadData.DokumentkravData.SannsynliggjøringData.Companion.toSannsynliggjøringData
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID

class PersonData(
    val ident: String,
    var søknader: List<SøknadData> = emptyList(),
    var aktivitetsLogg: AktivitetsloggData? = null
) {
    fun createPerson(): Søknadhåndterer {
        return Søknadhåndterer.rehydrer(
            ident = this.ident,
            aktivitetslogg = this.aktivitetsLogg?.konverterTilAktivitetslogg() ?: Aktivitetslogg(),
        ) { person ->
            søknader.map {
                Søknad.rehydrer(
                    søknadId = it.søknadsId,
                    søknadhåndterer = person,
                    ident = person.ident(),
                    dokument = it.dokumenter.rehydrer(),
                    journalpostId = it.journalpostId,
                    innsendtTidspunkt = it.innsendtTidspunkt,
                    språk = it.språkData.somSpråk(),
                    dokumentkrav = it.dokumentkrav.rehydrer(),
                    sistEndretAvBruker = it.sistEndretAvBruker,
                    tilstandsType = it.tilstandType.name
                )
            }.toMutableList()
        }
    }

    class SøknadData(
        val søknadsId: UUID,
        val tilstandType: TilstandData,
        var dokumenter: List<DokumentData>,
        val journalpostId: String?,
        val innsendtTidspunkt: ZonedDateTime?,
        val språkData: SpråkData,
        var dokumentkrav: DokumentkravData,
        val sistEndretAvBruker: ZonedDateTime?
    ) {
        class DokumentData(
            val urn: String
        ) {
            companion object {
                fun List<DokumentData>.rehydrer(): Søknad.Dokument? {
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

        class SpråkData(val verdi: String) {
            constructor(språk: Locale) : this(språk.toLanguageTag())

            fun somSpråk() = Språk(verdi)
        }

        data class DokumentkravData(
            val kravData: Set<KravData>
        ) {
            fun rehydrer(): Dokumentkrav = Dokumentkrav.rehydrer(
                krav = kravData.map { it.rehydrer() }.toSet()
            )

            data class SannsynliggjøringData(
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
                    fun Sannsynliggjøring.toSannsynliggjøringData() = SannsynliggjøringData(
                        id = this.id,
                        faktum = this.faktum().json,
                        sannsynliggjør = this.sannsynliggjør().map { it.json }.toSet()
                    )
                }
            }

            data class KravData(
                val id: String,
                val beskrivendeId: String,
                val sannsynliggjøring: SannsynliggjøringData,
                val tilstand: KravTilstandData,
                val filer: Set<FilData>
            ) {
                fun rehydrer() = Krav(
                    id = this.id,
                    filer = this.filer.map { it.rehydrer() }.toMutableSet(),
                    sannsynliggjøring = this.sannsynliggjøring.rehydrer(),
                    tilstand = when (this.tilstand) {
                        KravTilstandData.AKTIV -> Krav.KravTilstand.AKTIV
                        KravTilstandData.INAKTIV -> Krav.KravTilstand.INAKTIV
                    },
                )

                companion object {
                    fun Krav.toKravdata() = KravData(
                        id = this.id,
                        beskrivendeId = this.beskrivendeId,
                        filer = this.filer.map { it.toFilData() }.toSet(),
                        sannsynliggjøring = this.sannsynliggjøring.toSannsynliggjøringData(),
                        tilstand = when (this.tilstand) {
                            Krav.KravTilstand.AKTIV -> KravTilstandData.AKTIV
                            Krav.KravTilstand.INAKTIV -> KravTilstandData.INAKTIV
                        }
                    )
                }

                enum class KravTilstandData {
                    AKTIV,
                    INAKTIV
                }

                data class FilData(
                    val filnavn: String,
                    val urn: URN,
                    val storrelse: Long,
                    val tidspunkt: ZonedDateTime
                ) {

                    companion object {
                        fun Krav.Fil.toFilData() = FilData(
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

        enum class TilstandData {
            UnderOpprettelse,
            Påbegynt,
            AvventerArkiverbarSøknad,
            AvventerMidlertidligJournalføring,
            AvventerJournalføring,
            Journalført,
            Slettet;

            companion object {
                fun rehydrer(dbTilstand: String): TilstandData {
                    return TilstandData.valueOf(dbTilstand)
                }
            }
        }
    }

    data class AktivitetsloggData(
        val aktiviteter: List<AktivitetData>
    ) {
        data class AktivitetData(
            val alvorlighetsgrad: Alvorlighetsgrad,
            val label: Char,
            val behovtype: String?,
            val melding: String,
            val tidsstempel: String,
            val kontekster: List<SpesifikkKontekstData>,
            val detaljer: Map<String, Any>
        )

        data class SpesifikkKontekstData(
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

        private fun konverterTilAktivitetslogg(aktivitetsloggData: AktivitetsloggData): Aktivitetslogg {
            val aktiviteter = mutableListOf<Aktivitetslogg.Aktivitet>()
            aktivitetsloggData.aktiviteter.forEach {
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
