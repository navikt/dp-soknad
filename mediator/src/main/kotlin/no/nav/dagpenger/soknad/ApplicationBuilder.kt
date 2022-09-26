package no.nav.dagpenger.soknad

import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.livssyklus.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.soknad.livssyklus.JournalførtMottak
import no.nav.dagpenger.soknad.livssyklus.NyJournalpostMottak
import no.nav.dagpenger.soknad.livssyklus.ferdigstilling.FerdigstiltSøknadPostgresRepository
import no.nav.dagpenger.soknad.livssyklus.ferdigstilling.ferdigStiltSøknadRouteBuilder
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgaveMottak
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøknadCachePostgresRepository
import no.nav.dagpenger.soknad.livssyklus.start.SøknadOpprettetHendelseMottak
import no.nav.dagpenger.soknad.mal.SøknadMalPostgresRepository
import no.nav.dagpenger.soknad.mal.SøknadsMalMottak
import no.nav.dagpenger.soknad.observers.SøknadLoggerObserver
import no.nav.dagpenger.soknad.observers.SøknadSlettetObserver
import no.nav.dagpenger.soknad.personalia.KontonummerOppslag
import no.nav.dagpenger.soknad.personalia.PersonOppslag
import no.nav.dagpenger.soknad.personalia.personaliaRouteBuilder
import no.nav.dagpenger.soknad.sletterutine.UtdaterteSøknaderJob
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.clean
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.runMigration
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
        søknadMalRepository = søknadMalRepository,
        ferdigstiltSøknadRepository = ferdigstiltRepository,
        søknadRepository = SøknadPostgresRepository(PostgresDataSourceBuilder.dataSource),
        søknadObservers = listOf(
            SøknadLoggerObserver,
            SøknadSlettetObserver(rapidsConnection)
        )
    ).also {
        SøknadOpprettetHendelseMottak(rapidsConnection, it)
        ArkiverbarSøknadMottattHendelseMottak(rapidsConnection, it)
        NyJournalpostMottak(rapidsConnection, it)
        JournalførtMottak(rapidsConnection, it)
        SøkerOppgaveMottak(rapidsConnection, it)
    }

    init {
        rapidsConnection.register(this)
    }

    fun start() {
        rapidsConnection.start()
    }

    override fun onShutdown(rapidsConnection: RapidsConnection) {
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        clean()
        runMigration()
        SøknadsMalMottak(rapidsConnection, søknadMalRepository)
        UtdaterteSøknaderJob.sletterutine(søknadMediator)
    }

    private fun søknadMediator(): SøknadMediator = søknadMediator
}
