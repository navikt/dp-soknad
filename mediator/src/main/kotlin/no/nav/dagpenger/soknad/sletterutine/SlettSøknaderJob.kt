package no.nav.dagpenger.soknad.sletterutine

import mu.KotlinLogging
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.minutes

internal object SlettSøknaderJob {
    private val logger = KotlinLogging.logger {}

    fun sletterutine(søknadMediator: SøknadMediator) {
        val vaktmesterRepository = VaktmesterPostgresRepository(PostgresDataSourceBuilder.dataSource, søknadMediator)

        fixedRateTimer(
            name = "Sletterutine for søknader som ligger til sletting",
            daemon = true,
            initialDelay = 1.minutes.inWholeMilliseconds,
            period = 15.minutes.inWholeMilliseconds,
            action = {
                try {
                    vaktmesterRepository.slett()
                } catch (e: Exception) {
                    logger.error(e) { "Sletting av søknader feilet" }
                }
            }
        )
    }
}
