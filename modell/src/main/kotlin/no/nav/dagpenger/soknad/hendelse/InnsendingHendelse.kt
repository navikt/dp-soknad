package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

abstract class InnsendingHendelse(
    val innsendingId: UUID,
    søknadID: UUID,
    ident: String,
    aktivitetslogg: Aktivitetslogg
) : SøknadHendelse(søknadID, ident, aktivitetslogg)
