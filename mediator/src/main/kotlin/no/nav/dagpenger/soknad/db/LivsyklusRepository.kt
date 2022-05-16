package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

interface LivsyklusRepository {
    fun hent(ident: String): Person?
    fun lagre(person: Person)
    fun hentPåbegynte(personIdent: String): List<PåbegyntSøknad>
    fun lagreInnsendtTidpunkt(søknadID: UUID, innsendtidspunkt: ZonedDateTime)
    fun hentInnsendtTidspunkt(søknadID: UUID): ZonedDateTime?
}

interface VakmesterLivsyklusRepository {
    fun slettPåbegynteSøknaderEldreEnn(tidspunkt: LocalDateTime): Int
}
