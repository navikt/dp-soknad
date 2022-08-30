package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Krav
import java.util.UUID

sealed class DokumentKravHendelse(søknadID: UUID, ident: String, val kravId: String) : SøknadHendelse(søknadID, ident)

class LeggTilFil(søknadID: UUID, ident: String, kravId: String, val fil: Krav.Fil) :
    DokumentKravHendelse(søknadID, ident, kravId)
