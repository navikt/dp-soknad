package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Krav
import java.util.UUID

class KravHendelse(søknadID: UUID, ident: String, val kravId: String, val fil: Krav.Fil) : SøknadHendelse(søknadID, ident)