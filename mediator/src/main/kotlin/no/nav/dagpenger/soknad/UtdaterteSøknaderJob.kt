package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder
import no.nav.dagpenger.soknad.db.VaktmesterPostgresRepository
import java.time.LocalDateTime
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
                    LocalDateTime.now().minusDays(7)
                )
            }
        )
    }
}
