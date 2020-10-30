package no.nav.dagpenger.quizshow.api

import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType

internal object Configuration {
    private val config = ConfigurationProperties.systemProperties() overriding EnvironmentVariables()
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
