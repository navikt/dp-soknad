package no.nav.dagpenger.soknad

import java.time.ZonedDateTime
import java.util.UUID

internal class TestSøknadhåndtererInspektør(søknadhåndterer: Søknadhåndterer) : SøknadhåndtererVisitor {

    lateinit var søknadId: UUID
    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    internal lateinit var personLogg: Aktivitetslogg

    init {
        søknadhåndterer.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        søknadhåndterer: Søknadhåndterer,
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

internal class TestSøknadInspektør(private val søknad: Søknad) : SøknadVisitor {

    lateinit var søknadId: UUID
    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    lateinit var dokumentkrav: Dokumentkrav
    internal lateinit var personLogg: Aktivitetslogg

    init {
        søknad.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        søknadhåndterer: Søknadhåndterer,
        tilstand: Søknad.Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
        this.søknadId = søknadId
        this.dokumentkrav = dokumentkrav
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }
}
