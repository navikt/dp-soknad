package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Tilstand
import java.time.ZonedDateTime
import java.util.UUID

interface TilstandVisitor {
    fun visitTilstand(tilstand: Tilstand.Type) {}
}

interface SøknadVisitor : TilstandVisitor {
    fun visitSøknad(
        søknadId: UUID,
        søknadObserver: SøknadObserver,
        ident: String,
        tilstand: Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
    }
}

interface SøknadhåndtererVisitor : SøknadVisitor, AktivitetsloggVisitor {
    fun visitSøknader(søknader: List<Søknad>) {}
    fun preVisitSøknader() {}
    fun postVisitSøknader() {}
}
