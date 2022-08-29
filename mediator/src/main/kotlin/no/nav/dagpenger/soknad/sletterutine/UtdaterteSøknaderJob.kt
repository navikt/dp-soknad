package no.nav.dagpenger.soknad.sletterutine

import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import java.time.LocalDateTime
import kotlin.concurrent.fixedRateTimer

internal object UtdaterteSøknaderJob {

    private val logger = KotlinLogging.logger {}
    private const val SYV_DAGER = 7
    private val hverTime: Long = 3600000

    fun sletterutine(søknadMediator: SøknadMediator) {
        fixedRateTimer(
            name = "Påbegynte søknader vaktmester",
            daemon = true,
            initialDelay = 300000L,
            period = hverTime,
            action = {
                logger.info("Slettejobb startet nå (${LocalDateTime.now()})")
                VaktmesterPostgresRepository(dataSource, søknadMediator).slettPåbegynteSøknaderEldreEnn(SYV_DAGER)
                logger.info("Slettejobb fullførte nå (${LocalDateTime.now()})")
            }
        )
    }
}
