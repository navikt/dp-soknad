package no.nav.dagpenger.soknad.serder

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.SpesifikkKontekst
import no.nav.dagpenger.soknad.Søknad
import java.util.UUID

class PersonData(
    val internId: Long,
    val ident: String,
    var søknader: List<SøknadData> = listOf(),
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
                    tilstandsType = it.tilstandType,
                    dokumentLokasjon = it.dokumentLokasjon,
                    journalpostId = it.journalpostId
                )
            }.toMutableList()
        }
    }

    class SøknadData(
        val søknadsId: UUID,
        val tilstandType: String,
        val dokumentLokasjon: String?,
        val journalpostId: String?
    )

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
