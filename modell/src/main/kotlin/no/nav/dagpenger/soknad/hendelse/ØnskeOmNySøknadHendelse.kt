package no.nav.dagpenger.soknad.hendelse

import no.nav.dagpenger.soknad.Aktivitetslogg
import java.util.UUID

class ØnskeOmNySøknadHendelse(ident: String, søknadID: UUID, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    SøknadHendelse(
        søknadID, ident, aktivitetslogg
    )

class HarPåbegyntSøknadHendelse(ident: String, søknadID: UUID, aktivitetslogg: Aktivitetslogg = Aktivitetslogg()) :
    SøknadHendelse(
        søknadID, ident, aktivitetslogg
    )

sealed class Søknadsprosess {
    abstract fun getSøknadsId(): UUID
}

class PåbegyntSøknadsProsess(private val søknadID: UUID) : Søknadsprosess() {
    override fun getSøknadsId(): UUID {
        return søknadID
    }
}

object NySøknadsProsess : Søknadsprosess() {
    override fun getSøknadsId(): UUID {
        return UUID.randomUUID()
    }
}
