package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Innsending.Dokument
import no.nav.dagpenger.soknad.Innsending.InnsendingType
import no.nav.dagpenger.soknad.Innsending.Metadata
import no.nav.dagpenger.soknad.Innsending.TilstandType
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
        tilstand: TilstandType,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Dokument?,
        dokumenter: List<Dokument>,
        metadata: Metadata? = null
    ) {
    }
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
        opprettet: ZonedDateTime,
        innsendt: ZonedDateTime?,
        tilstand: Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?
    ) {
    }
}
