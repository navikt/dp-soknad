package no.nav.dagpenger.soknad.serder

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.SpesifikkKontekst

data class AktivitetsloggDTO(
    val aktiviteter: List<AktivitetDTO>,
) {
    data class AktivitetDTO(
        val alvorlighetsgrad: Alvorlighetsgrad,
        val label: Char,
        val behovtype: String?,
        val melding: String,
        val tidsstempel: String,
        val kontekster: List<SpesifikkKontekstDTO>,
        val detaljer: Map<String, Any>,
    )

    data class SpesifikkKontekstDTO(
        val kontekstType: String,
        val kontekstMap: Map<String, String>,
    )

    enum class Alvorlighetsgrad {
        INFO,
        WARN,
        BEHOV,
        ERROR,
        SEVERE,
    }

    fun konverterTilAktivitetslogg(): Aktivitetslogg = konverterTilAktivitetslogg(this)

    private fun konverterTilAktivitetslogg(aktivitetsloggDTO: AktivitetsloggDTO): Aktivitetslogg {
        val aktiviteter = mutableListOf<Aktivitetslogg.Aktivitet>()
        aktivitetsloggDTO.aktiviteter.forEach {
            val kontekster = it.kontekster.map { spesifikkKontekstData ->
                SpesifikkKontekst(
                    spesifikkKontekstData.kontekstType,
                    spesifikkKontekstData.kontekstMap,
                )
            }
            aktiviteter.add(
                when (it.alvorlighetsgrad) {
                    Alvorlighetsgrad.INFO -> Aktivitetslogg.Aktivitet.Info(
                        kontekster,
                        it.melding,
                        it.tidsstempel,
                    )
                    Alvorlighetsgrad.WARN -> Aktivitetslogg.Aktivitet.Warn(
                        kontekster,
                        it.melding,
                        it.tidsstempel,
                    )
                    Alvorlighetsgrad.BEHOV -> Aktivitetslogg.Aktivitet.Behov(
                        Aktivitetslogg.Aktivitet.Behov.Behovtype.valueOf(it.behovtype!!),
                        kontekster,
                        it.melding,
                        it.detaljer,
                        it.tidsstempel,
                    )
                    Alvorlighetsgrad.ERROR -> Aktivitetslogg.Aktivitet.Error(
                        kontekster,
                        it.melding,
                        it.tidsstempel,
                    )
                    Alvorlighetsgrad.SEVERE -> Aktivitetslogg.Aktivitet.Severe(
                        kontekster,
                        it.melding,
                        it.tidsstempel,
                    )
                },
            )
        }
        return Aktivitetslogg.rehyder(
            aktiviteter = aktiviteter,
        )
    }
}
