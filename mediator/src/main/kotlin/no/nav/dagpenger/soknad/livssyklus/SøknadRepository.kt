package no.nav.dagpenger.soknad.livssyklus

import no.nav.dagpenger.soknad.Søknad
import java.time.LocalDate
import java.util.UUID

interface SøknadRepository {
    fun hentEier(søknadId: UUID): String?
    fun hent(søknadId: UUID, ident: String): Søknad?
    fun hent(søknadId: UUID): Søknad?
    fun hentSøknader(ident: String): Set<Søknad>
    fun lagre(søknad: Søknad)
    fun hentPåbegyntSøknad(personIdent: String): PåbegyntSøknad?
}

data class PåbegyntSøknad(val uuid: UUID, val startDato: LocalDate, val språk: String)
