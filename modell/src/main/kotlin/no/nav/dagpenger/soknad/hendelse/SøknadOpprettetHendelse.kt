package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.SpesifikkKontekst
import java.util.UUID

class SøknadOpprettetHendelse(private val søknadID: UUID, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) : Hendelse(aktivitetslogg) {
    internal fun søknadID() = søknadID

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return this.javaClass.canonicalName.split('.').last().let {
            SpesifikkKontekst(it, mapOf("søknadID" to søknadID.toString()))
        }
    }
}
