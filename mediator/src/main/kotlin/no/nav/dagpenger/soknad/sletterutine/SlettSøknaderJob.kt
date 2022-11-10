package no.nav.dagpenger.soknad.sletterutine

import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import kotlin.concurrent.fixedRateTimer

internal object SlettSøknaderJob {
    private val logger = KotlinLogging.logger {}

    fun sletterutine(søknadMediator: SøknadMediator) {
        val vaktmesterRepository = VaktmesterPostgresRepository(PostgresDataSourceBuilder.dataSource, søknadMediator)

        fixedRateTimer(
            name = "Sletterutine for søknader som ligger til sletting",
            daemon = true,
            initialDelay = 1.Minutt,
            period = 5.Minutt,
            action = {
                try {
                    vaktmesterRepository.slett()
                } catch (e: Exception) {
                    logger.error { "Sletting av søknader feilet: $e" }
                }
            }
        )
    }

    private val Int.Minutt get() = this * 1000L
}
