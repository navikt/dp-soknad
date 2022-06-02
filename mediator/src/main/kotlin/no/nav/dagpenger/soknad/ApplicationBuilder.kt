package no.nav.dagpenger.soknad

import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.soknad.db.FerdigstiltSøknadPostgresRepository
import no.nav.dagpenger.soknad.db.LivsyklusPostgresRepository
import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder
import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.soknad.db.SøknadCachePostgresRepository
import no.nav.dagpenger.soknad.db.SøknadMalPostgresRepository
import no.nav.dagpenger.soknad.mottak.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.soknad.mottak.JournalførtMottak
import no.nav.dagpenger.soknad.mottak.NyJournalpostMottak
import no.nav.dagpenger.soknad.mottak.SøkerOppgaveMottak
import no.nav.dagpenger.soknad.mottak.SøknadOpprettetHendelseMottak
import no.nav.dagpenger.soknad.mottak.SøknadsMalMottak
import no.nav.dagpenger.soknad.observers.PersonLoggerObserver
import no.nav.dagpenger.soknad.personalia.KontonummerOppslag
import no.nav.dagpenger.soknad.personalia.PersonOppslag
import no.nav.dagpenger.soknad.personalia.personaliaRouteBuilder
import no.nav.dagpenger.soknad.søknad.ferdigStiltSøknadRouteBuilder
import no.nav.dagpenger.soknad.søknad.søknadApiRouteBuilder
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
        livsyklusRepository = LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource),
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
        SøkerOppgaveMottak(rapidsConnection, it)
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
        runMigration()
        SøknadsMalMottak(rapidsConnection, søknadMalRepository)
    }

    private fun søknadMediator(): SøknadMediator = søknadMediator
}
