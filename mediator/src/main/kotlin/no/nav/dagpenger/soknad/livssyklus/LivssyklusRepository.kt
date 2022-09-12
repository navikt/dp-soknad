package no.nav.dagpenger.soknad.livssyklus

import no.nav.dagpenger.soknad.Søknad
import java.time.LocalDate
import java.util.UUID

interface SøknadRepository {
    fun hent(søknadId: UUID, ident: String, komplettAktivitetslogg: Boolean = false): Søknad?
    fun hentSøknader(ident: String, komplettAktivitetslogg: Boolean = false): Set<Søknad>
    fun lagre(søknad: Søknad)
    fun hentPåbegyntSøknad(personIdent: String): PåbegyntSøknad?
}

data class PåbegyntSøknad(val uuid: UUID, val startDato: LocalDate, val språk: String)
