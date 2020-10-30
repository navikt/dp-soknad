package no.nav.dagpenger.quizshow.api

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(configuration: Configuration) : RapidsConnection.StatusListener {
    private val rapidsConnection = RapidApplication.Builder(
        RapidApplication.RapidApplicationConfig.fromEnv(configuration.rapidApplication)
    ).withKtorModule {
        søknadApi(::subscribe)
    }.build()

    private val mediator = Mediator(rapidsConnection)

    init {
        rapidsConnection.register(this)
    }

    fun start() = rapidsConnection.start()
    fun stop() = rapidsConnection.stop()
    override fun onStartup(rapidsConnection: RapidsConnection) {
    }

    private fun subscribe(meldingObserver: MeldingObserver) {
        mediator.register(meldingObserver)
    }
}
