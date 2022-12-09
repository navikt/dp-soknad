package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadVisitor
import java.time.ZonedDateTime
import java.util.UUID

class TestSøknadVisitor(søknad: Søknad) : SøknadVisitor {
    lateinit var dokumentKrav: Dokumentkrav
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
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?,
    ) {
        this.dokumentKrav = dokumentkrav
        this.innsendt = innsendt
    }
}
