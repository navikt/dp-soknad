package no.nav.dagpenger.soknad.hendelse.innsending

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class InnsendingPåminnelseHendelse(
    innsendingId: UUID,
    ident: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) : InnsendingHendelse(innsendingId, ident, aktivitetslogg)
