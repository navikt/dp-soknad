package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Språk
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.SøknadVisitor
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class SøknadInnsendtHendelse(
    søknadID: UUID,
    ident: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
    søknad: Søknad? = null,
) :
    SøknadHendelse(
        søknadID,
        ident,
        aktivitetslogg
    ),
    SøknadVisitor {

    fun copy(søknad: Søknad): SøknadInnsendtHendelse {
        return SøknadInnsendtHendelse(
            søknadID = this.søknadID(),
            ident = this.ident(),
            aktivitetslogg = this.aktivitetslogg,
            søknad = søknad
        )
    }

    private val innsendtTidspunkt = ZonedDateTime.now(ZoneId.of("Europe/Oslo")).truncatedTo(ChronoUnit.SECONDS)
    fun innsendtidspunkt(): ZonedDateTime = innsendtTidspunkt

    var prosessversjon: Prosessversjon? = null
    var tilstand: Søknad.Tilstand? = null

    init {
        søknad?.accept(this)
    }

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

        this.prosessversjon = prosessversjon
        this.tilstand = tilstand
    }
}
