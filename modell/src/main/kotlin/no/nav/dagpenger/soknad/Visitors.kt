package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.Søknad.Tilstand
import java.time.ZonedDateTime
import java.util.UUID

interface TilstandVisitor {
    fun visitTilstand(tilstand: Tilstand.Type) {}
}

interface DokumentkravVisitor {
    fun visitAktiveKrav(krav: Set<Krav>) {}
    fun visitInaktiveKrav(krav: Set<Krav>) {}
    fun visitSannsynliggjøringer(sannsynliggjøringer: Set<Sannsynliggjøring>) {}
}
interface SøknadVisitor : TilstandVisitor, DokumentkravVisitor {
    fun visitSøknad(søknadId: UUID, person: Person, tilstand: Tilstand, dokument: Søknad.Dokument?, journalpostId: String?, innsendtTidspunkt: ZonedDateTime?, språk: Språk) {}
}

interface PersonVisitor : SøknadVisitor, AktivitetsloggVisitor {
    fun visitPerson(ident: String) {}
    fun visitPerson(ident: String, søknader: List<Søknad>) {}
    fun preVisitSøknader() {}
    fun postVisitSøknader() {}
}
