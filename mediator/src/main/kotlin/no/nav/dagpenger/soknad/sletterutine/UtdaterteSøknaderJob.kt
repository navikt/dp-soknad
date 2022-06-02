package no.nav.dagpenger.soknad.sletterutine

import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder
import java.time.LocalDateTime.now
import kotlin.concurrent.fixedRateTimer

object UtdaterteSøknaderJob {
    fun sletterutine() {
        fixedRateTimer(
            name = "Påbegynte søknader vaktmester",
            daemon = true,
            initialDelay = 300000L,
            period = 86400000,
            action = {
                VaktmesterPostgresRepository(PostgresDataSourceBuilder.dataSource).slettPåbegynteSøknaderEldreEnn(
                    now().minusDays(7)
                )
            }
        )
    }
}
