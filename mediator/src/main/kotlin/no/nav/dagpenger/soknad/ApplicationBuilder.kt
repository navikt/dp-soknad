package no.nav.dagpenger.soknad

import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.soknad.auth.AuthFactory
import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder
import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.soknad.observers.PersonLoggerObserver
import no.nav.dagpenger.soknad.personalia.KontonummerOppslag
import no.nav.dagpenger.soknad.personalia.PersonOppslag
import no.nav.dagpenger.soknad.søknad.Mediator
import no.nav.dagpenger.soknad.søknad.MeldingObserver
import no.nav.dagpenger.soknad.søknad.PostgresPersistence
import no.nav.dagpenger.soknad.søknad.SøknadStore
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(config: Map<String, String>) : RapidsConnection.StatusListener {

    private val rapidsConnection: RapidsConnection = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig.fromEnv(config)
    ).withKtorModule {
        if (System.getenv()["NAIS_CLUSTER_NAME"] == "dev-gcp") {
            søknadApi(
                jwkProvider = AuthFactory.jwkProvider,
                issuer = AuthFactory.issuer,
                clientId = AuthFactory.clientId,
                store = store(),
                personOppslag = PersonOppslag(createPersonOppslag(Configuration.pdlUrl)),
                søknadMediator = søknadMediator(),
                kontonummerOppslag = KontonummerOppslag(
                    dpProxyUrl = Configuration.dpProxyUrl,
                    tokenProvider = { Configuration.dpProxyTokenProvider.clientCredentials(Configuration.dpProxyScope).accessToken },
                ),
            )
        }
    }.build()

    val persistence = PostgresPersistence(
        PostgresDataSourceBuilder.dataSource
    )

    private val mediator = Mediator(rapidsConnection, persistence)
    private val søknadMediator = SøknadMediator(
        rapidsConnection = rapidsConnection,
        personRepository = persistence,
        personObservers = listOf(
            PersonLoggerObserver
        )
    )

    init {
        rapidsConnection.register(this)
    }

    fun start() = rapidsConnection.start()
    fun stop() = rapidsConnection.stop()

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        persistence.close()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        runMigration()
    }

    private fun store(): SøknadStore = mediator
    private fun søknadMediator(): SøknadMediator = søknadMediator

    private fun subscribe(meldingObserver: MeldingObserver) {
        mediator.register(meldingObserver)
    }
}
