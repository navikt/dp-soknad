package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Innsending.Dokument
import no.nav.dagpenger.soknad.Innsending.InnsendingType
import no.nav.dagpenger.soknad.Innsending.Metadata
import no.nav.dagpenger.soknad.Innsending.TilstandType
import no.nav.dagpenger.soknad.Søknad.Tilstand
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

interface TilstandVisitor {
    fun visitTilstand(tilstand: Tilstand.Type) {}
}

interface InnsendingVisitor {
    fun visit(
        innsendingId: UUID,
        søknadId: UUID,
        ident: String,
        innsendingType: InnsendingType,
        tilstand: TilstandType,
        sistEndretTilstand: LocalDateTime,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Dokument?,
        dokumenter: List<Dokument>,
        metadata: Metadata? = null,
    ) {
    }
}

interface DokumentkravVisitor {
    fun visitKrav(krav: Krav) {}
}

interface SøknadVisitor :
    TilstandVisitor,
    AktivitetsloggVisitor,
    DokumentkravVisitor {
    fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        innsendt: ZonedDateTime?,
        tilstand: Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?,
    ) {
    }
}
