package no.nav.dagpenger.soknad

import java.time.ZonedDateTime
import java.util.UUID

internal class TestSøknadhåndtererInspektør(søknadhåndterer: Søknad) : SøknadVisitor {
    lateinit var gjeldendetilstand: Søknad.Tilstand.Type
    lateinit var gjeldendeSøknadId: String
    var aktivitetslogg: Map<String, List<Map<String, Any>>> = emptyMap()
    var antallSøknader = 0

    init {
        søknadhåndterer.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        innsendt: ZonedDateTime?,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?
    ) {
        this.gjeldendeSøknadId = søknadId.toString()
        this.antallSøknader++
    }

    override fun visitTilstand(tilstand: Søknad.Tilstand.Type) {
        gjeldendetilstand = tilstand
    }

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        this.aktivitetslogg = aktivitetslogg.toMap()
    }
}
