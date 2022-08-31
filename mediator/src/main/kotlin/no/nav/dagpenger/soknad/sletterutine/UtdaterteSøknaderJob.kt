package no.nav.dagpenger.soknad.sletterutine

import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random

internal object UtdaterteSøknaderJob {
    private const val SYV_DAGER = 7

    fun sletterutine(søknadMediator: SøknadMediator) {
        fixedRateTimer(
            name = "Påbegynte søknader vaktmester",
            daemon = true,
            initialDelay = 3000L,
            period = Random.nextLong(3000000L, 3600000L),
            action = {
                VaktmesterPostgresRepository(dataSource, søknadMediator).slettPåbegynteSøknaderEldreEnn(SYV_DAGER)
            }
        )
    }
}
