package no.nav.dagpenger.søknad.hendelse

import no.nav.dagpenger.søknad.Aktivitetslogg
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class SøknadInnsendtHendelse(søknadID: UUID, ident: String, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    SøknadHendelse(
        søknadID,
        ident,
        aktivitetslogg
    ) {
    private val innsendtTidspunkt = ZonedDateTime.now(ZoneId.of("Europe/Oslo")).truncatedTo(ChronoUnit.SECONDS)
    fun innsendtidspunkt(): ZonedDateTime = innsendtTidspunkt
}
