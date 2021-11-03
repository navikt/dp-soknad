package no.nav.dagpenger.quizshow.api

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(config: Map<String, String>) : RapidsConnection.StatusListener {

    private val rapidsConnection = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig.fromEnv(config)
    ).withKtorModule {
        søknadWebsocketApi(::subscribe)
        if (System.getenv()["NAIS_CLUSTER_NAME"] == "dev-gcp") {
            søknadApi(store())
        }
    }.build()

    private val mediator = Mediator(rapidsConnection)

    init {
        rapidsConnection.register(this)
    }

    fun start() = rapidsConnection.start()
    fun stop() = rapidsConnection.stop()

    override fun onStartup(rapidsConnection: RapidsConnection) {
    }

    private fun store(): SøknadStore = mediator

    private fun subscribe(meldingObserver: MeldingObserver) {
        mediator.register(meldingObserver)
    }
}
