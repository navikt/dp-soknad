package no.nav.dagpenger.soknad.minesoknader

import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Innsending.Dokument
import no.nav.dagpenger.soknad.Innsending.InnsendingType
import no.nav.dagpenger.soknad.Innsending.Metadata
import no.nav.dagpenger.soknad.Innsending.TilstandType
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadVisitor
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class MineSøknaderVisitor(søknad: Søknad) : SøknadVisitor {

    private lateinit var søknadOpprettet: LocalDateTime
    private var søknadSistEndretAvBruker: LocalDateTime? = null
    private lateinit var søknadTilstand: Søknad.Tilstand.Type
    private val søknadInnsendinger: MutableList<LocalDateTime> = mutableListOf()
    private lateinit var prosessversjon: Prosessversjon

    init {
        søknad.accept(this)
    }

    fun førsteInnsendingTidspunkt() = søknadInnsendinger.minOf { it }
    fun sistEndretAvBruker() = søknadSistEndretAvBruker
    fun søknadOpprettet() = søknadOpprettet
    fun søknadTilstand() = søknadTilstand
    fun prosessversjon() = prosessversjon

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime,
        prosessversjon: Prosessversjon?
    ) {
        require(prosessversjon != null) { "Prosessversjon kan ikke være null for søknad $søknadId" }
        søknadOpprettet = opprettet.toLocalDateTime()
        søknadSistEndretAvBruker = sistEndretAvBruker.toLocalDateTime()
        søknadTilstand = tilstand.tilstandType
        this.prosessversjon = prosessversjon
    }

    override fun visit(
        innsendingId: UUID,
        innsending: InnsendingType,
        tilstand: TilstandType,
        innsendt: ZonedDateTime,
        journalpost: String?,
        hovedDokument: Dokument?,
        dokumenter: List<Dokument>,
        metadata: Metadata?
    ) {
        søknadInnsendinger.add(innsendt.toLocalDateTime())
    }
}
