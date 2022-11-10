package no.nav.dagpenger.soknad.sletterutine

import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import kotlin.concurrent.fixedRateTimer

internal object UtdaterteSøknaderJob {
    private const val SYV_DAGER = 7
    private val logger = KotlinLogging.logger {}

    fun sletterutine(søknadMediator: SøknadMediator) {
        val vaktmesterRepository = VaktmesterPostgresRepository(dataSource, søknadMediator)

        fixedRateTimer(
            name = "Sletterutine for påbegynte søknader uendret siste $SYV_DAGER",
            daemon = true,
            initialDelay = 1.Minutt,
            period = 15.Minutt,
            action = {
                try {
                    vaktmesterRepository.markerUtdaterteTilSletting(SYV_DAGER)
                } catch (e: Exception) {
                    logger.error { "Sletterutine feilet: $e" }
                }
            }
        )
    }

    private val Int.Minutt get() = this * 1000L
}
