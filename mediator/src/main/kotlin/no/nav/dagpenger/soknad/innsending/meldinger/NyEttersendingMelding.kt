package no.nav.dagpenger.soknad.innsending.meldinger

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import no.nav.dagpenger.soknad.Innsending

class NyEttersendingMelding(
    packet: JsonMessage,
) : MeldingOmInnsending(packet) {
    override val innsending = Innsending.ettersending(innsendt, ident, søknadId, dokumentkrav)
}
