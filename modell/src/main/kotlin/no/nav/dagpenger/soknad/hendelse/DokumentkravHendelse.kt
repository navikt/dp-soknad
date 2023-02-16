package no.nav.dagpenger.soknad.hendelse

import de.slub.urn.URN
import no.nav.dagpenger.soknad.Krav
import no.nav.dagpenger.soknad.SpesifikkKontekst
import java.util.UUID

sealed class DokumentKravHendelse(val søknadID: UUID, ident: String, val kravId: String) : Hendelse(ident) {
    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst(
            this.klasseNavn,
            mapOf(
                "søknad_uuid" to søknadID.toString(),
                "krav_id" to kravId
            )
        )
    }
}

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
) : DokumentKravHendelse(søknadID, ident, kravId)

class DokumentKravSammenstilling(
    søknadID: UUID,
    ident: String,
    kravId: String,
    private val urn: URN,
) : DokumentKravHendelse(søknadID, ident, kravId) {
    fun urn() = urn
}
