package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.mottak.SøkerOppgave
import java.time.LocalDateTime
import java.util.UUID

interface SøknadCacheRepository {
    fun lagre(søkerOppgave: SøkerOppgave)
    fun slett(søknadUUID: UUID, eier: String): Boolean
    fun hent(søknadUUID: UUID): SøkerOppgave?
}

interface LivsyklusRepository {
    fun hent(ident: String): Person?
    fun lagre(person: Person)
    fun hentPåbegynte(personIdent: String): List<PåbegyntSøknad>
}

interface VakmesterLivsyklusRepository {
    fun slettPåbegynteSøknaderEldreEnn(tidspunkt: LocalDateTime): Int
}
