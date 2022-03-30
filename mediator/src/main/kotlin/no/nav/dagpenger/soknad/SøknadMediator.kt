package no.nav.dagpenger.soknad

import mu.KotlinLogging
import no.nav.dagpenger.soknad.db.PersonRepository
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.helse.rapids_rivers.RapidsConnection

internal class SøknadMediator(
    rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository
) {
    private companion object {
        val sikkerLogger = KotlinLogging.logger("tjenestekall")
    }

    private val behovMediator = BehovMediator(rapidsConnection, sikkerLogger)

    fun behandle(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        behandle(søknadOpprettetHendelse) { person ->
            person.håndter(søknadOpprettetHendelse)
        }
    }

    private fun behandle(hendelse: Hendelse, håndterer: (Person) -> Unit) {
        val person = hentEllerOpprettPerson(hendelse)
        håndterer(person)
        behovMediator.håndter(hendelse)
    }

    private fun hentEllerOpprettPerson(hendelse: Hendelse) =
        personRepository.hent(hendelse.ident()) ?: Person(hendelse.ident())
}
