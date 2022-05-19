package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.hendelse.Dokument
import java.time.ZonedDateTime
import java.util.UUID

internal class TestSøknadInspektør(person: Person) : PersonVisitor {

    lateinit var søknadId: UUID
    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    internal lateinit var personLogg: Aktivitetslogg

    init {
        person.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        person: Person,
        tilstand: Søknad.Tilstand,
        dokument: Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?
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
