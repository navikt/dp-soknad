package no.nav.dagpenger.quizshow.api

import com.natpryce.konfig.ConfigurationProperties.Companion.systemProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.stringType
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import com.natpryce.konfig.overriding

internal class ApplicationBuilder(configuration: Configuration) : RapidsConnection.StatusListener {
    private val rapidsConnection = RapidApplication.Builder(
            RapidApplication.RapidApplicationConfig.fromEnv(configuration.rapidApplication)
    ).withKtorModule {
        søknadApi()
        //naisApi()
    }.build()
    init {
        rapidsConnection.register(this)
    }
    fun start() = rapidsConnection.start()
    fun stop() = rapidsConnection.stop()
    override fun onStartup(rapidsConnection: RapidsConnection) {
    }
}

internal object Configuration {
    private val config = systemProperties() overriding EnvironmentVariables()
    val rapidApplication: Map<String, String> = mutableMapOf(
            "RAPID_APP_NAME" to "dp-quiz",
            "KAFKA_BOOTSTRAP_SERVERS" to config.getOrElse(Key("kafka.bootstrap.servers", stringType), "localhost:9092"),
            "KAFKA_CONSUMER_GROUP_ID" to "dp-quiz-v1",
            "KAFKA_RAPID_TOPIC" to config.getOrElse(Key("kafka.topic", stringType), "privat-dagpenger-behov-v2"),
            "KAFKA_RESET_POLICY" to config.getOrElse(Key("kafka.reset.policy", stringType), "earliest"),
            "NAV_TRUSTSTORE_PASSWORD" to config.getOrElse(Key("nav.truststore.password", stringType), "/non/existing"),
            "HTTP_PORT" to config.getOrElse(Key("port", stringType), "8080")
    ).also {
        config.getOrNull(Key("nav.truststore.path", stringType))?.let { truststore ->
            it["NAV_TRUSTSTORE_PATH"] = truststore
        }
    }
}