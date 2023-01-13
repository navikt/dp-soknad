package no.nav.dagpenger.soknad.innsending.meldinger

import no.nav.dagpenger.soknad.Innsending
import no.nav.helse.rapids_rivers.JsonMessage

class NyInnsendingMelding(packet: JsonMessage) : MeldingOmInnsending(packet) {
    override val innsending = Innsending.ny(innsendt, ident, søknadId, dokumentkrav)
    override fun hendelse(): NyInnsendingHendelse = NyInnsendingHendelse(innsending, ident)
}
