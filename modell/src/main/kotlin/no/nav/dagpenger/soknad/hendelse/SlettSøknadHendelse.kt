package no.nav.dagpenger.soknad.hendelse

import java.util.UUID

data class SlettSøknadHendelse(val søknadID: UUID, val ident: String) :
    SøknadHendelse(
        søknadID,
        ident,
    )
