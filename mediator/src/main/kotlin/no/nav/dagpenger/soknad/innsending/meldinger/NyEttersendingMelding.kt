package no.nav.dagpenger.soknad.innsending.meldinger

import no.nav.dagpenger.soknad.Innsending
import no.nav.helse.rapids_rivers.JsonMessage

class NyEttersendingMelding(packet: JsonMessage) : MeldingOmInnsending(packet) {
    override val innsending = Innsending.ettersending(innsendt, ident, søknadId, dokumentkrav)
    override fun hendelse(): NyInnsendingHendelse = NyInnsendingHendelse(innsending, ident)
}
