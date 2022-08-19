package no.nav.dagpenger.soknad.sletterutine

import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import kotlin.concurrent.fixedRateTimer

object UtdaterteSøknaderJob {

    private const val SYV_DAGER = 7

    fun sletterutine() {
        fixedRateTimer(
            name = "Påbegynte søknader vaktmester",
            daemon = true,
            initialDelay = 300000L,
            period = 86400000,
            action = {
                VaktmesterPostgresRepository(PostgresDataSourceBuilder.dataSource).slettPåbegynteSøknaderEldreEnn(SYV_DAGER)
            }
        )
    }
}
