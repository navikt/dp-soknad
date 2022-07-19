package no.nav.dagpenger.soknad.serder

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.SpesifikkKontekst
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.serder.PersonData.SøknadData.DokumentData.Companion.rehydrer
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID

class PersonData(
    val ident: String,
    var søknader: List<SøknadData> = emptyList(),
    var aktivitetsLogg: AktivitetsloggData? = null
) {
    fun createPerson(): Person {
        return Person.rehydrer(
            ident = this.ident,
            aktivitetslogg = this.aktivitetsLogg?.konverterTilAktivitetslogg() ?: Aktivitetslogg(),
        ) { p ->
            søknader.map {
                Søknad.rehydrer(
                    søknadId = it.søknadsId,
                    person = p,
                    tilstandsType = it.tilstandType.name,
                    dokument = it.dokumenter.rehydrer(),
                    journalpostId = it.journalpostId,
                    innsendtTidspunkt = it.innsendtTidspunkt,
                    språk = it.språkData.somSpråk()
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
        val språkData: SpråkData
    ) {
        class DokumentData(
            val urn: String,
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
                aktiviteter = aktiviteter,
            )
        }
    }
}
