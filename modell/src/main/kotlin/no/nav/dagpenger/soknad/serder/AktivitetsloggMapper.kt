package no.nav.dagpenger.soknad.serder

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.AktivitetsloggVisitor
import no.nav.dagpenger.soknad.SpesifikkKontekst

class AktivitetsloggMapper(aktivitetslogg: Aktivitetslogg) {

    enum class Alvorlighetsgrad {
        INFO,
        WARN,
        BEHOV,
        ERROR,
        SEVERE
    }

    private val aktiviteter = Aktivitetslogginspektør(aktivitetslogg).aktiviteter

    internal fun toMap() = mutableMapOf(
        "aktiviteter" to aktiviteter
    )

    private inner class Aktivitetslogginspektør(aktivitetslogg: Aktivitetslogg) : AktivitetsloggVisitor {
        internal val aktiviteter = mutableListOf<Map<String, Any>>()

        init {
            aktivitetslogg.accept(this)
        }

        override fun visitInfo(
            kontekster: List<SpesifikkKontekst>,
            aktivitet: Aktivitetslogg.Aktivitet.Info,
            melding: String,
            tidsstempel: String
        ) {
            leggTilMelding(kontekster, Alvorlighetsgrad.INFO, melding, tidsstempel)
        }

        override fun visitWarn(
            kontekster: List<SpesifikkKontekst>,
            aktivitet: Aktivitetslogg.Aktivitet.Warn,
            melding: String,
            tidsstempel: String
        ) {
            leggTilMelding(kontekster, Alvorlighetsgrad.WARN, melding, tidsstempel)
        }

        override fun visitBehov(
            kontekster: List<SpesifikkKontekst>,
            aktivitet: Aktivitetslogg.Aktivitet.Behov,
            type: Aktivitetslogg.Aktivitet.Behov.Behovtype,
            melding: String,
            detaljer: Map<String, Any>,
            tidsstempel: String
        ) {
            leggTilBehov(
                kontekster,
                Alvorlighetsgrad.BEHOV,
                type,
                melding,
                detaljer,
                tidsstempel
            )
        }

        override fun visitError(
            kontekster: List<SpesifikkKontekst>,
            aktivitet: Aktivitetslogg.Aktivitet.Error,
            melding: String,
            tidsstempel: String
        ) {
            leggTilMelding(kontekster, Alvorlighetsgrad.ERROR, melding, tidsstempel)
        }

        override fun visitSevere(
            kontekster: List<SpesifikkKontekst>,
            aktivitet: Aktivitetslogg.Aktivitet.Severe,
            melding: String,
            tidsstempel: String
        ) {
            leggTilMelding(kontekster, Alvorlighetsgrad.SEVERE, melding, tidsstempel)
        }

        private fun leggTilMelding(
            kontekster: List<SpesifikkKontekst>,
            alvorlighetsgrad: Alvorlighetsgrad,
            melding: String,
            tidsstempel: String
        ) {
            aktiviteter.add(
                mutableMapOf<String, Any>(
                    "kontekster" to map(kontekster),
                    "alvorlighetsgrad" to alvorlighetsgrad.name,
                    "melding" to melding,
                    "detaljer" to emptyMap<String, Any>(),
                    "tidsstempel" to tidsstempel
                )
            )
        }

        private fun leggTilBehov(
            kontekster: List<SpesifikkKontekst>,
            alvorlighetsgrad: Alvorlighetsgrad,
            type: Aktivitetslogg.Aktivitet.Behov.Behovtype,
            melding: String,
            detaljer: Map<String, Any>,
            tidsstempel: String
        ) {
            aktiviteter.add(
                mutableMapOf<String, Any>(
                    "kontekster" to map(kontekster),
                    "alvorlighetsgrad" to alvorlighetsgrad.name,
                    "behovtype" to type.toString(),
                    "melding" to melding,
                    "detaljer" to detaljer,
                    "tidsstempel" to tidsstempel
                )
            )
        }

        private fun map(kontekster: List<SpesifikkKontekst>): List<Map<String, Any>> {
            return kontekster.map {
                mutableMapOf(
                    "kontekstType" to it.kontekstType,
                    "kontekstMap" to it.kontekstMap
                )
            }
        }
    }
}
