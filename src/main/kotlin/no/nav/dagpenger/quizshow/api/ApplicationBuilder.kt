package no.nav.dagpenger.quizshow.api

import no.nav.dagpenger.pdl.createPersonOppslag
import no.nav.dagpenger.quizshow.api.auth.AuthFactory
import no.nav.dagpenger.quizshow.api.personalia.KontonummerOppslag
import no.nav.dagpenger.quizshow.api.personalia.PersonOppslag
import no.nav.dagpenger.quizshow.api.søknad.RedisPersistence
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(config: Map<String, String>) : RapidsConnection.StatusListener {

    private val rapidsConnection = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig.fromEnv(config)
    ).withKtorModule {
        if (System.getenv()["NAIS_CLUSTER_NAME"] == "dev-gcp") {
            søknadApi(
                jwkProvider = AuthFactory.jwkProvider,
                issuer = AuthFactory.issuer,
                clientId = AuthFactory.clientId,
                store = store(),
                personOppslag = PersonOppslag(createPersonOppslag(Configuration.pdlUrl)),
                kontonummerOppslag = KontonummerOppslag(
                    dpProxyUrl = Configuration.dpProxyUrl,
                    tokenProvider = { Configuration.dpProxyTokenProvider.clientCredentials(Configuration.dpProxyScope).accessToken },
                )
            )
        }
    }.build()

    val persistence = RedisPersistence(
        config["REDIS_HOST"] ?: throw IllegalStateException("REDIS_HOST missing"),
        config["REDIS_PASSWORD"] ?: throw IllegalStateException("REDIS_PASSWORD missing"),
    )

    private val mediator = Mediator(
        rapidsConnection,
        persistence
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
    }

    private fun store(): SøknadStore = mediator

    private fun subscribe(meldingObserver: MeldingObserver) {
        mediator.register(meldingObserver)
    }
}
