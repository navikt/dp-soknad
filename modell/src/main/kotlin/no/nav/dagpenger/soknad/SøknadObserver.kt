package no.nav.dagpenger.soknad

import java.util.UUID

interface SøknadObserver {
    fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) {}
    fun innsendingTilstandEndret(event: SøknadInnsendingEndretTilstandEvent) {}

    fun søknadSlettet(event: SøknadSlettetEvent) {}
    fun søknadMigrert(event: SøknadMigrertEvent) {}

    data class SøknadEndretTilstandEvent(
        val søknadId: UUID,
        val ident: String,
        val prosessversjon: Prosessversjon?,
        val gjeldendeTilstand: Søknad.Tilstand.Type,
        val forrigeTilstand: Søknad.Tilstand.Type
    )

    data class SøknadInnsendingEndretTilstandEvent(
        val søknadId: UUID,
        val innsending: InnsendingObserver.InnsendingEndretTilstandEvent
    )

    data class SøknadSlettetEvent(
        val søknadId: UUID,
        val ident: String
    )

    data class SøknadMigrertEvent(
        val søknkadId: UUID,
        val ident: String,
        val forrigeProsessversjon: Prosessversjon,
        val gjeldendeProsessversjon: Prosessversjon
    )
}
