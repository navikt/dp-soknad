package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Tilstand
import java.time.ZonedDateTime
import java.util.UUID

interface TilstandVisitor {
    fun visitTilstand(tilstand: Tilstand.Type) {}
}

interface DokumentkravVisitor {
    fun preVisitDokumentkrav() {}
    fun visitKrav(krav: Krav) {}
    fun postVisitDokumentkrav() {}
}

interface SøknadVisitor : TilstandVisitor, AktivitetsloggVisitor, DokumentkravVisitor {
    fun visitSøknad(
        søknadId: UUID,
        ident: String,
        tilstand: Tilstand,
        journalpost: Søknad.Journalpost?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
    }
}
