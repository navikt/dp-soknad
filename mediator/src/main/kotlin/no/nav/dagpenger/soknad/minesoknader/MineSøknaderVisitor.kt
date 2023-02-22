package no.nav.dagpenger.soknad.minesoknader

import no.nav.dagpenger.soknad.DokumentkravVisitor
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadVisitor
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class MineSøknaderVisitor(søknad: Søknad) : SøknadVisitor, DokumentkravVisitor {

    private var søknadInnsendt: ZonedDateTime? = null
    private lateinit var søknadOpprettet: LocalDateTime
    private var søknadSistEndretAvBruker: LocalDateTime? = null
    private lateinit var søknadTilstand: Søknad.Tilstand.Type

    init {
        søknad.accept(this)
    }

    internal fun søknadInnsendt(): LocalDateTime = requireNotNull(søknadInnsendt).toLocalDateTime()
    internal fun sistEndretAvBruker() = søknadSistEndretAvBruker
    internal fun søknadOpprettet() = søknadOpprettet
    internal fun søknadTilstand() = søknadTilstand

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        innsendt: ZonedDateTime?,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?,
    ) {
        søknadOpprettet = opprettet.toLocalDateTime()
        søknadSistEndretAvBruker = sistEndretAvBruker.toLocalDateTime()
        søknadTilstand = tilstand.tilstandType
        søknadInnsendt = innsendt
    }
}
