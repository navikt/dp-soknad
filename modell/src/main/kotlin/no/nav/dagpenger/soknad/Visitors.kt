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
        innsending: Innsending.InnsendingType,
        tilstand: Innsending.Tilstand.Type,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: List<Søknad.Journalpost.Variant>?,
        vedlegg: List<Innsending.Vedlegg>
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
