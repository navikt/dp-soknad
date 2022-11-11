package no.nav.dagpenger.soknad.livssyklus

import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Companion.erPåbegynt
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

    fun hentPåbegynteSøknader(prosessversjon: Prosessversjon): List<Søknad>

    class OptimistiskLåsingException(val søknadId: UUID, val databaseVersjon: Int, val nyVersjon: Int) :
        RuntimeException("Kunne ikke oppdatere søknadId: $søknadId database versjon: '$databaseVersjon' ny versjon: '$nyVersjon'")
}
