package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Tilstand
import java.time.ZonedDateTime
import java.util.UUID

interface TilstandVisitor {
    fun visitTilstand(tilstand: Tilstand.Type) {}
}

interface InnsendingVisitor {
    fun preVisitEttersendinger() {}
    fun postVisitEttersendinger() {}
    fun visit(
        innsendingId: UUID,
        innsending: Innsending.InnsendingType,
        tilstand: Innsending.Tilstand.Type,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Innsending.Dokument?,
        dokumenter: List<Innsending.Dokument>
    ) {}
}
interface DokumentkravVisitor {
    fun preVisitDokumentkrav() {}
    fun visitKrav(krav: Krav) {}
    fun postVisitDokumentkrav() {}
}

interface SøknadVisitor : TilstandVisitor, AktivitetsloggVisitor, DokumentkravVisitor, InnsendingVisitor {
    fun visitSøknad(
        søknadId: UUID,
        ident: String,
        tilstand: Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime?
    ) {
    }
}
