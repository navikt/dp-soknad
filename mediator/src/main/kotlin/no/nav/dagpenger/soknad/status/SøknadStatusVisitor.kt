package no.nav.dagpenger.soknad.status

import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadVisitor
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class SøknadStatusVisitor(søknad: Søknad) : SøknadVisitor {

    private var søknadInnsendt: ZonedDateTime? = null
    private lateinit var søknadOpprettet: LocalDateTime
    private lateinit var søknadTilstand: Søknad.Tilstand.Type

    init {
        søknad.accept(this)
    }

    fun søknadInnsendt() = requireNotNull(søknadInnsendt)
    fun søknadOpprettet() = søknadOpprettet
    fun søknadTilstand() = søknadTilstand

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
        søknadOpprettet = opprettet.toLocalDateTime()
        søknadTilstand = tilstand.tilstandType
        søknadInnsendt = innsendt
    }
}
