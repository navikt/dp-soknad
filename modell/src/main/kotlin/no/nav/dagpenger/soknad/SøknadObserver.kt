package no.nav.dagpenger.soknad

import java.util.UUID

interface SøknadObserver {
    fun søknadTilstandEndret(event: SøknadEndretTilstandEvent) {}

    fun søknadSlettet(event: SøknadSlettetEvent) {}

    data class SøknadEndretTilstandEvent(
        val søknadId: UUID,
        val gjeldendeTilstand: Søknad.Tilstand.Type,
        val forrigeTilstand: Søknad.Tilstand.Type
    )

    data class SøknadSlettetEvent(
        val søknadId: UUID,
        val ident: String
    )
}
