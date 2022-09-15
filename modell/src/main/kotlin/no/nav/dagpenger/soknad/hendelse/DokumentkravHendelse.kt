package no.nav.dagpenger.soknad.hendelse

import de.slub.urn.URN
import no.nav.dagpenger.soknad.Krav
import java.util.UUID

sealed class DokumentKravHendelse(søknadID: UUID, ident: String, val kravId: String) : SøknadHendelse(søknadID, ident)

class LeggTilFil(søknadID: UUID, ident: String, kravId: String, val fil: Krav.Fil) :
    DokumentKravHendelse(søknadID, ident, kravId)

class SlettFil(søknadID: UUID, ident: String, kravId: String, val urn: URN) :
    DokumentKravHendelse(søknadID, ident, kravId)

class DokumentasjonIkkeTilgjengelig(
    søknadID: UUID,
    ident: String,
    kravId: String,
    val valg: Krav.Svar.SvarValg,
    val begrunnelse: String?
) :
    DokumentKravHendelse(søknadID, ident, kravId)

class DokumentasjonkravFerdigstilt(søknadID: UUID, ident: String, kravId: String, val ferdigstiltURN: URN) :
    DokumentKravHendelse(søknadID, ident, kravId)

class DokumentKravBundleSvar(søknadID: UUID, ident: String, kravId: String, private val urn: URN,
) : DokumentKravHendelse(søknadID, ident, kravId) {
    fun urn() = urn
}