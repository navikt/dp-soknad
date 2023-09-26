package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.soknad.Sannsynliggjøring
import no.nav.dagpenger.soknad.Søknad
import java.util.UUID

fun interface SøkeroppgaveHendelseObserver {
    // Kalles når en hendelse har blitt håndtert av modellen uten feil
    fun håndtert(søkeroppgaveHendelse: SøkeroppgaveHendelse)
}

class SøkeroppgaveHendelse(
    søknadUUID: UUID,
    eier: String,
    private val sannsynliggjøringer: Set<Sannsynliggjøring>,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : SøknadHendelse(søknadUUID, eier, aktivitetslogg) {
    private val observers = mutableListOf<SøkeroppgaveHendelseObserver>()
    fun addObserver(block: SøkeroppgaveHendelseObserver) {
        observers.add(block)
    }

    fun håndter(søknad: Søknad) {
        søknad.håndter(sannsynliggjøringer)
        if (harFunksjonelleFeilEllerVerre()) return
        observers.forEach { it.håndtert(this) }
    }
}
