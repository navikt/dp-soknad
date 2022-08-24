package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Sannsynliggjøring
import java.util.UUID

class SøkeroppgaveHendelse(
    søknadUUID: UUID,
    eier: String,
    private val sannsynliggjøringer: Set<Sannsynliggjøring>,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : SøknadHendelse(søknadUUID, eier, aktivitetslogg) {

    fun sannsynliggjøringer() = sannsynliggjøringer
}
