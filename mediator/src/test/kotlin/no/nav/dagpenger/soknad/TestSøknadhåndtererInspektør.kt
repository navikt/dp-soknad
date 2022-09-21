package no.nav.dagpenger.soknad

import java.time.ZonedDateTime
import java.util.UUID

internal class TestSøknadhåndtererInspektør(søknadhåndterer: Søknad) : SøknadVisitor {
    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    lateinit var gjeldendeSøknadId: String
    var innsendtTidspunkt: ZonedDateTime? = null
    var aktivitetslogg: Map<String, List<Map<String, Any>>> = emptyMap()
    var antallSøknader = 0
    lateinit var gjeldendeInnsendingId: UUID
    lateinit var gjeldendeInnsendingTilstand: NyInnsending.Tilstand.Type

    init {
        søknadhåndterer.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        this.gjeldendeSøknadId = søknadId.toString()
        this.antallSøknader++
    }

    override fun visit(
            innsendingId: UUID,
            innsending: NyInnsending.InnsendingType,
            tilstand: NyInnsending.Tilstand.Type,
            innsendt: ZonedDateTime,
            journalpost: String?,
            hovedDokument: NyInnsending.Dokument?,
            dokumenter: List<NyInnsending.Dokument>,
            brevkode: NyInnsending.Brevkode?
    ) {
        this.innsendtTidspunkt = innsendt
        this.gjeldendeInnsendingId = innsendingId
        this.gjeldendeInnsendingTilstand = tilstand
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        this.aktivitetslogg = aktivitetslogg.toMap()
    }
}
