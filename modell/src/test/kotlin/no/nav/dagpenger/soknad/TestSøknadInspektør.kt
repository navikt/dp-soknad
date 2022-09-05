package no.nav.dagpenger.soknad

import java.time.ZonedDateTime
import java.util.UUID

internal class TestSøknadInspektør(søknadhåndterer: Søknadhåndterer) : SøknadhåndtererVisitor {

    lateinit var søknadId: UUID
    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    internal lateinit var personLogg: Aktivitetslogg

    init {
        søknadhåndterer.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        søknadObserver: SøknadObserver,
        ident: String,
        tilstand: Søknad.Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        this.søknadId = søknadId
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }

    override fun postVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        personLogg = aktivitetslogg
    }
}
