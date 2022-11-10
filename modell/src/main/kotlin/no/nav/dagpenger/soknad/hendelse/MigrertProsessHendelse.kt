package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Prosessversjon
import java.util.UUID

class MigrertProsessHendelse(
    val søknadId: UUID,
    ident: String,
    val prosessversjon: Prosessversjon,
    aktivitetslogg: Aktivitetslogg = Aktivitetslogg()
) : SøknadHendelse(søknadId, ident, aktivitetslogg)
