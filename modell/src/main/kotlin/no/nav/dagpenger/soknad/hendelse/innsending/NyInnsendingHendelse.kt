package no.nav.dagpenger.soknad.innsending.meldinger

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingHendelse

class NyInnsendingHendelse(
    val innsending: Innsending,
    ident: String,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg(),
) :
    InnsendingHendelse(innsending.innsendingId, ident, aktivitetslogg)
