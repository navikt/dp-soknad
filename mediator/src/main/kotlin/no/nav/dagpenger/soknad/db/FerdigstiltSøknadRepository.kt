package no.nav.dagpenger.soknad.db

import java.util.UUID

interface FerdigstiltSøknadRepository {
    fun lagreSøknadsTekst(søknadUuid: UUID, søknadsTekst: String)
    fun hentTekst(søknadId: UUID): String
    fun hentFakta(søknadId: UUID): String
}
