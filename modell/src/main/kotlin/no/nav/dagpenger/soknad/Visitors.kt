package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Innsending.Brevkode
import no.nav.dagpenger.soknad.Innsending.Dokument
import no.nav.dagpenger.soknad.Innsending.InnsendingType
import no.nav.dagpenger.soknad.Innsending.Tilstand.Type
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
        innsending: InnsendingType,
        tilstand: Type,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Dokument?,
        dokumenter: List<Dokument>,
        brevkode: Brevkode? = null
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
