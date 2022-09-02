package no.nav.dagpenger.soknad.hendelse

import de.slub.urn.URN
import no.nav.dagpenger.soknad.Krav
import java.util.UUID

sealed class DokumentKravHendelse(søknadID: UUID, ident: String, val kravId: String) : SøknadHendelse(søknadID, ident)

class LeggTilFil(søknadID: UUID, ident: String, kravId: String, val fil: Krav.Fil) :
    DokumentKravHendelse(søknadID, ident, kravId)

class SlettFil(søknadID: UUID, ident: String, kravId: String, val urn: URN) :
    DokumentKravHendelse(søknadID, ident, kravId)

class DokumentasjonIkkeTilgjengelig(søknadID: UUID, ident: String, kravId: String, val svar: String) :
    DokumentKravHendelse(søknadID, ident, kravId)
