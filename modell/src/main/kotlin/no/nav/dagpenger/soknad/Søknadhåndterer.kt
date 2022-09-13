package no.nav.dagpenger.soknad

class Søknadhåndterer constructor(
    søknadsfunksjon: (søknadhåndterer: Søknadhåndterer) -> MutableList<Søknad>
) : SøknadObserver {

    private val observers = mutableListOf<SøknadObserver>()

    override fun søknadTilstandEndret(event: SøknadObserver.SøknadEndretTilstandEvent) {
        observers.forEach {
            it.søknadTilstandEndret(event)
        }
    }

    override fun søknadSlettet(event: SøknadObserver.SøknadSlettetEvent) {
        observers.forEach {
            it.søknadSlettet(event)
        }
    }
}
