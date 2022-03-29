package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.db.PersonRepository
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.helse.rapids_rivers.RapidsConnection

internal class SøknadMediator(rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository
) {


    fun håndter(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        val person = personRepository.hent(ønskeOmNySøknadHendelse.ident())
        if(person != null) {
            person.håndter(ønskeOmNySøknadHendelse)
            personRepository.lagre(person)
        } else {
            val nyPerson = Person(ønskeOmNySøknadHendelse.ident())
            nyPerson.håndter(ønskeOmNySøknadHendelse)
        }



        // lagre ny person?
        // delegere ønskeOmNySøknadHendelse til person?

    }
}