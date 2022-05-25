package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.søknad.SøkerOppgave
import java.time.LocalDateTime
import java.util.UUID

interface LivsyklusRepository {
    fun hent(ident: String): Person?
    fun hent(søknadUUID: UUID): SøkerOppgave?
    fun invalider(søknadUUID: UUID, eier: String): Boolean
    fun lagre(søkerOppgave: SøkerOppgave)
    fun lagre(person: Person)
    fun hentPåbegynte(personIdent: String): List<PåbegyntSøknad>
}

interface VakmesterLivsyklusRepository {
    fun slettPåbegynteSøknaderEldreEnn(tidspunkt: LocalDateTime): Int
}
