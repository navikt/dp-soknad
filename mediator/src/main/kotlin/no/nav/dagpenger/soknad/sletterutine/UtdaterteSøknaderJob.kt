package no.nav.dagpenger.soknad.sletterutine

import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.dataSource
import kotlin.concurrent.fixedRateTimer

internal object UtdaterteSøknaderJob {

    private const val SYV_DAGER = 7

    fun sletterutine(søknadMediator: SøknadMediator) {
        fixedRateTimer(
            name = "Påbegynte søknader vaktmester",
            daemon = true,
            initialDelay = 300000L,
            period = 86400000,
            action = {
                VaktmesterPostgresRepository(dataSource, søknadMediator).slettPåbegynteSøknaderEldreEnn(SYV_DAGER)
            }
        )
    }
}
