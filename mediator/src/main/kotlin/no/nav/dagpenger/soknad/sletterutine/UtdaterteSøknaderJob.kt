package no.nav.dagpenger.soknad.sletterutine

import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import java.time.LocalDateTime.now
import kotlin.concurrent.fixedRateTimer

object UtdaterteSøknaderJob {

    private const val DAGER_FØR_PÅBEGYNTE_SØKNADER_SLETTES = 7L

    fun sletterutine() {
        fixedRateTimer(
            name = "Påbegynte søknader vaktmester",
            daemon = true,
            initialDelay = 300000L,
            period = 86400000,
            action = {
                VaktmesterPostgresRepository(PostgresDataSourceBuilder.dataSource).slettPåbegynteSøknaderEldreEnn(
                    now().minusDays(DAGER_FØR_PÅBEGYNTE_SØKNADER_SLETTES)
                )
            }
        )
    }
}
