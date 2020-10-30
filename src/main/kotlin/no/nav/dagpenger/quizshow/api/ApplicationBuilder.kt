package no.nav.dagpenger.quizshow.api

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(configuration: Configuration) : RapidsConnection.StatusListener {
    private val mediator = Mediator()
    private val rapidsConnection = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig.fromEnv(configuration.rapidApplication)
    ).withKtorModule {
        søknadApi(mediator)
    }.build()
    init {
        rapidsConnection.register(this)
        mediator.register(rapidsConnection)
    }
    fun start() = rapidsConnection.start()
    fun stop() = rapidsConnection.stop()
    override fun onStartup(rapidsConnection: RapidsConnection) {
    }
}
