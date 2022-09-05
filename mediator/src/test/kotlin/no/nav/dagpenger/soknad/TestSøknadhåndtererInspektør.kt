package no.nav.dagpenger.soknad

import java.time.ZonedDateTime
import java.util.UUID

internal class TestSøknadhåndtererInspektør(søknadhåndterer: Søknadhåndterer) : SøknadhåndtererVisitor {
    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    lateinit var gjeldendeSøknadId: String
    var innsendtTidspunkt: ZonedDateTime? = null
    var aktivitetslogg: Map<String, List<Map<String, Any>>> = emptyMap()
    var antallSøknader = 0

    init {
        søknadhåndterer.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        søknadObserver: SøknadObserver,
        tilstand: Søknad.Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        this.gjeldendeSøknadId = søknadId.toString()
        this.innsendtTidspunkt = innsendtTidspunkt
        this.antallSøknader++
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        this.aktivitetslogg = aktivitetslogg.toMap()
    }
}
