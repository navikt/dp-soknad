package no.nav.dagpenger.soknad.status

import no.nav.dagpenger.soknad.Dokumentkrav
import no.nav.dagpenger.soknad.Innsending.Dokument
import no.nav.dagpenger.soknad.Innsending.InnsendingType
import no.nav.dagpenger.soknad.Innsending.Metadata
import no.nav.dagpenger.soknad.Innsending.TilstandType
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadVisitor
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

class SøknadStatusVisitor(søknad: Søknad) : SøknadVisitor {

    private lateinit var søknadOpprettet: LocalDateTime
    private lateinit var søknadTilstand: Søknad.Tilstand.Type
    private val søknadInnsendinger: MutableList<LocalDateTime> = mutableListOf()

    init {
        søknad.accept(this)
    }

    fun førsteInnsendingTidspunkt() = søknadInnsendinger.minOf { it }
    fun søknadOpprettet() = søknadOpprettet
    fun søknadTilstand() = søknadTilstand

    override fun visitSøknad(
        søknadId: UUID,
        ident: String,
        opprettet: ZonedDateTime,
        tilstand: Søknad.Tilstand,
        språk: Språk,
        dokumentkrav: Dokumentkrav,
        sistEndretAvBruker: ZonedDateTime
    ) {
        søknadOpprettet = opprettet.toLocalDateTime()
        søknadTilstand = tilstand.tilstandType
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
