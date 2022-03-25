package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class ArkiverbarSøknadMotattHendelse(
    søknadID: UUID,
    private val dokumentLokasjon: DokumentLokasjon,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : SøknadHendelse(søknadID, aktivitetslogg) {

    internal fun dokumentLokasjon() = dokumentLokasjon
}

typealias DokumentLokasjon = String
