package no.nav.dagpenger.soknad.minesoknader

import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadVisitor
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class MineSøknaderVisitor(søknad: Søknad) : SøknadVisitor {

    private var søknadInnsendt: ZonedDateTime? = null
    private lateinit var søknadOpprettet: LocalDateTime
    private var søknadSistEndretAvBruker: LocalDateTime? = null
    private lateinit var søknadTilstand: Søknad.Tilstand.Type
    private lateinit var dokumentkrav: Set<Krav>

    init {
        søknad.accept(this)
    }

    internal fun søknadInnsendt(): LocalDateTime = requireNotNull(søknadInnsendt).toLocalDateTime()
    internal fun sistEndretAvBruker() = søknadSistEndretAvBruker
    internal fun søknadOpprettet() = søknadOpprettet
    internal fun søknadTilstand() = søknadTilstand
    internal fun dokumentkrav() = dokumentkrav

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
        søknadSistEndretAvBruker = sistEndretAvBruker.toLocalDateTime()
        søknadTilstand = tilstand.tilstandType
        søknadInnsendt = innsendt
        this.dokumentkrav = dokumentkrav.aktiveDokumentKrav()
    }
}
