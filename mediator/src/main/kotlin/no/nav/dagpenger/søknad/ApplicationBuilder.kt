package no.nav.dagpenger.søknad

import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.søknad.livssyklus.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.søknad.livssyklus.JournalførtMottak
import no.nav.dagpenger.søknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.søknad.livssyklus.NyJournalpostMottak
import no.nav.dagpenger.søknad.livssyklus.ferdigstilling.FerdigstiltSøknadPostgresRepository
import no.nav.dagpenger.søknad.livssyklus.påbegynt.SøkerOppgaveMottak
import no.nav.dagpenger.søknad.livssyklus.påbegynt.SøknadCachePostgresRepository
import no.nav.dagpenger.søknad.livssyklus.start.SøknadOpprettetHendelseMottak
import no.nav.dagpenger.søknad.mal.SøknadMalPostgresRepository
import no.nav.dagpenger.søknad.mal.SøknadsMalMottak
import no.nav.dagpenger.søknad.observers.PersonLoggerObserver
import no.nav.dagpenger.søknad.personalia.KontonummerOppslag
import no.nav.dagpenger.søknad.personalia.PersonOppslag
import no.nav.dagpenger.søknad.personalia.personaliaRouteBuilder
import no.nav.dagpenger.søknad.sletterutine.UtdaterteSøknaderJob
import no.nav.dagpenger.søknad.søknad.ferdigStiltSøknadRouteBuilder
import no.nav.dagpenger.søknad.utils.db.PostgresDataSourceBuilder
import no.nav.dagpenger.søknad.utils.db.PostgresDataSourceBuilder.clean
import no.nav.dagpenger.søknad.utils.db.PostgresDataSourceBuilder.runMigration
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(config: Map<String, String>) : RapidsConnection.StatusListener {

    private val rapidsConnection: RapidsConnection = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig.fromEnv(config)
    ).withKtorModule {
        if (System.getenv()["NAIS_CLUSTER_NAME"] == "dev-gcp") {
            api(
                personaliaRouteBuilder = personaliaRouteBuilder(
                    personOppslag = PersonOppslag(createPersonOppslag(Configuration.pdlUrl)),
                    kontonummerOppslag = KontonummerOppslag(
                        dpProxyUrl = Configuration.dpProxyUrl,
                        tokenProvider = { Configuration.dpProxyTokenProvider.clientCredentials(Configuration.dpProxyScope).accessToken },
                    )
                ),
                søknadRouteBuilder = søknadApiRouteBuilder(søknadMediator()),
                ferdigstiltRouteBuilder = ferdigStiltSøknadRouteBuilder(
                    ferdigstiltRepository
                )
            )
        }
    }.build()

    private val søknadMalRepository = SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource)
    private val ferdigstiltRepository = FerdigstiltSøknadPostgresRepository(
        PostgresDataSourceBuilder.dataSource
    )

    private val søknadMediator = SøknadMediator(
        rapidsConnection = rapidsConnection,
        søknadCacheRepository = SøknadCachePostgresRepository(PostgresDataSourceBuilder.dataSource),
        livssyklusRepository = LivssyklusPostgresRepository(PostgresDataSourceBuilder.dataSource),
        søknadMalRepository = søknadMalRepository,
        ferdigstiltSøknadRepository = ferdigstiltRepository,
        personObservers = listOf(
            PersonLoggerObserver
        )
    ).also {
        SøknadOpprettetHendelseMottak(rapidsConnection, it)
        ArkiverbarSøknadMottattHendelseMottak(rapidsConnection, it)
        NyJournalpostMottak(rapidsConnection, it)
        JournalførtMottak(rapidsConnection, it)
        // SøkerOppgaveMottak(rapidsConnection, it)
    }

    init {
        rapidsConnection.register(this)
    }

    fun start() {
        rapidsConnection.start()
        UtdaterteSøknaderJob.sletterutine()
    }

    override fun onShutdown(rapidsConnection: RapidsConnection) {
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        clean()
        runMigration()
        SøknadsMalMottak(rapidsConnection, søknadMalRepository)
    }

    private fun søknadMediator(): SøknadMediator = søknadMediator
}
