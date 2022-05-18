package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.hendelse.DokumentLokasjon
import java.time.ZonedDateTime
import java.util.UUID

internal class TestPersonInspektør(person: Person) : PersonVisitor {

    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    lateinit var gjeldendeSøknadId: String
    var innsendtTidspunkt: ZonedDateTime? = null

    init {
        person.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        person: Person,
        tilstand: Søknad.Tilstand,
        dokumentLokasjon: DokumentLokasjon?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?
    ) {
        this.gjeldendeSøknadId = søknadId.toString()
        this.innsendtTidspunkt = innsendtTidspunkt
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }
}
