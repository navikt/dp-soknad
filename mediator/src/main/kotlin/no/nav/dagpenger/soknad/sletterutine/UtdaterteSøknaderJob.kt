package no.nav.dagpenger.soknad.sletterutine

import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import kotlin.concurrent.fixedRateTimer

private val logger = KotlinLogging.logger {}

internal object UtdaterteSøknaderJob {
    private const val SYV_DAGER = 7

    fun sletterutine(søknadMediator: SøknadMediator) {
        val vaktmesterRepository = VaktmesterPostgresRepository(dataSource, søknadMediator)

        fixedRateTimer(
            name = "Sletterutine for påbegynte søknader uendret siste $SYV_DAGER",
            daemon = true,
            initialDelay = 60L,
            period = 60L,
            action = {
                try {
                    vaktmesterRepository.slettPåbegynteSøknaderEldreEnn(SYV_DAGER)
                    vaktmesterRepository.slettSlettede()
                } catch (e: Exception) {
                    logger.error { "Sletterutine feilet: $e" }
                }
            }
        )
    }
}
