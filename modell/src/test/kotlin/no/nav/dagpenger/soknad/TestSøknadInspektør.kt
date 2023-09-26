package no.nav.dagpenger.soknad

import java.time.ZonedDateTime
import java.util.UUID
import no.nav.dagpenger.soknad.Aktivitetslogg
internal class TestSøknadInspektør(søknad: Søknad) : SøknadVisitor {
    lateinit var søknadId: UUID
    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    lateinit var dokumentkrav: Dokumentkrav
    lateinit var aktivitetslogg: no.nav.dagpenger.soknad.Aktivitetslogg
    lateinit var innsending: InnsendingData
    lateinit var opprettet: ZonedDateTime

    data class InnsendingData(
        val innsendingId: UUID,
        val innsending: Innsending.InnsendingType,
        val tilstand: Innsending.TilstandType,
        val innsendt: ZonedDateTime,
        val journalpost: String?,
        val hovedDokument: Innsending.Dokument?,
        val dokumenter: List<Innsending.Dokument>,
    )

    init {
        søknad.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        innsendt: ZonedDateTime?,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?,
    ) {
        this.søknadId = søknadId
        this.dokumentkrav = dokumentkrav
        this.opprettet = opprettet
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }

    override fun postVisitAktivitetslogg(aktivitetslogg: no.nav.dagpenger.soknad.Aktivitetslogg) {
        this.aktivitetslogg = aktivitetslogg
    }
}
