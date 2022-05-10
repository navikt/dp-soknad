package no.nav.dagpenger.soknad

import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.soknad.auth.AuthFactory
import no.nav.dagpenger.soknad.db.LivsyklusPostgresRepository
import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder
import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.soknad.db.SøknadMalPostgresRepository
import no.nav.dagpenger.soknad.db.VaktmesterPostgresRepository
import no.nav.dagpenger.soknad.mottak.ArkiverbarSøknadMottattHendelseMottak
import no.nav.dagpenger.soknad.mottak.JournalførtMottak
import no.nav.dagpenger.soknad.mottak.NyJournalpostMottak
import no.nav.dagpenger.soknad.mottak.SøknadOpprettetHendelseMottak
import no.nav.dagpenger.soknad.mottak.SøknadsMalMottak
import no.nav.dagpenger.soknad.observers.PersonLoggerObserver
import no.nav.dagpenger.soknad.personalia.KontonummerOppslag
import no.nav.dagpenger.soknad.personalia.PersonOppslag
import no.nav.dagpenger.soknad.personalia.personaliaRouteBuilder
import no.nav.dagpenger.soknad.søknad.Mediator
import no.nav.dagpenger.soknad.søknad.PostgresPersistence
import no.nav.dagpenger.soknad.søknad.SøknadStore
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.LocalDateTime
import kotlin.concurrent.fixedRateTimer

internal class ApplicationBuilder(config: Map<String, String>) : RapidsConnection.StatusListener {

    private val rapidsConnection: RapidsConnection = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig.fromEnv(config)
    ).withKtorModule {
        if (System.getenv()["NAIS_CLUSTER_NAME"] == "dev-gcp") {
            api(
                jwkProvider = AuthFactory.jwkProvider,
                issuer = AuthFactory.issuer,
                clientId = AuthFactory.clientId,
                store = store(),
                søknadMediator = søknadMediator(),
                personaliaRouteBuilder = personaliaRouteBuilder(
                    personOppslag = PersonOppslag(createPersonOppslag(Configuration.pdlUrl)),
                    kontonummerOppslag = KontonummerOppslag(
                        dpProxyUrl = Configuration.dpProxyUrl,
                        tokenProvider = { Configuration.dpProxyTokenProvider.clientCredentials(Configuration.dpProxyScope).accessToken },
                    )
                ),
            )
        }
    }.build()

    val persistence = PostgresPersistence(
        PostgresDataSourceBuilder.dataSource
    )

    private val mediator = Mediator(rapidsConnection, persistence)
    private val søknadMalRepository = SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource)

    private val søknadMediator = SøknadMediator(
        rapidsConnection = rapidsConnection,
        livsyklusRepository = LivsyklusPostgresRepository(PostgresDataSourceBuilder.dataSource),
        søknadMalRepository = søknadMalRepository,
        personObservers = listOf(
            PersonLoggerObserver
        )
    ).also {
        SøknadOpprettetHendelseMottak(rapidsConnection, it)
        ArkiverbarSøknadMottattHendelseMottak(rapidsConnection, it)
        NyJournalpostMottak(rapidsConnection, it)
        JournalførtMottak(rapidsConnection, it)
    }

    init {
        rapidsConnection.register(this)
    }

    fun start() {
        rapidsConnection.start()
        // todo ekstraher til egnet sted
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

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        persistence.close()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        runMigration()
        SøknadsMalMottak(rapidsConnection, søknadMalRepository)
    }

    private fun store(): SøknadStore = mediator
    private fun søknadMediator(): SøknadMediator = søknadMediator
}
