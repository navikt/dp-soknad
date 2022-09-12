package no.nav.dagpenger.soknad.sletterutine

import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

internal object UtdaterteSøknaderJob {
    private const val SYV_DAGER = 7

    fun sletterutine(søknadMediator: SøknadMediator) {
        fixedRateTimer(
            name = "Påbegynte søknader vaktmester",
            daemon = true,
            initialDelay = 3000L,
            period = Random.nextLong(3000000L, 3600000L),
            action = {
                try {
                    VaktmesterPostgresRepository(dataSource, søknadMediator).slettPåbegynteSøknaderEldreEnn(SYV_DAGER)
                } catch (e: Exception) {
                    logger.error { "Sletterutine feilet: $e" }
                }
            }
        )
    }
}
