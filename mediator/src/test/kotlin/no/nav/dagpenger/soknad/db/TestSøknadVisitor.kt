package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadVisitor
import java.time.ZonedDateTime
import java.util.UUID

class TestSøknadVisitor(søknad: Søknad) : SøknadVisitor {
    var innsendt: ZonedDateTime? = null

    init {
        søknad.accept(this)
    }

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        innsendt: ZonedDateTime?,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?
    ) {
        this.innsendt = innsendt
    }
}
