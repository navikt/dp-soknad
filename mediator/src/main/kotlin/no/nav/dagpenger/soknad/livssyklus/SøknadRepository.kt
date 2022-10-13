package no.nav.dagpenger.soknad.livssyklus

import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Companion.erPåbegynt
import java.time.LocalDateTime
import java.util.UUID

interface SøknadRepository {
    fun hentEier(søknadId: UUID): String?
    fun hent(søknadId: UUID): Søknad?
    fun hentSøknader(ident: String): Set<Søknad>
    fun lagre(søknad: Søknad)
    fun hentPåbegyntSøknad(personIdent: String): Søknad? {
        val søknader = hentSøknader(personIdent)
        return søknader.firstOrNull { søknad ->
            søknad.erPåbegynt()
        }
    }

    fun hentTilstand(søknadId: UUID): Søknad.Tilstand.Type?
    fun hentOpprettet(søknadId: UUID): LocalDateTime?
}
