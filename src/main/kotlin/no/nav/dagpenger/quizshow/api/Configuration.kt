package no.nav.dagpenger.quizshow.api

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config

internal object Configuration {

    const val appName = "dp-quizshow-api"

    private val defaultProperties = ConfigurationMap(
        mapOf(
            "RAPID_APP_NAME" to "dp-quizshow-api",
            "KAFKA_CONSUMER_GROUP_ID" to "dp-quizshow-api-v1",
            "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
            "KAFKA_RESET_POLICY" to "latest",
        )
    )
    val properties =
        ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties

    val config: Map<String, String> = properties.list().reversed().fold(emptyMap()) { map, pair ->
        map + pair.second
    }

    val basePath = "/arbeid/dagpenger/soknadapi"

    val Configuration.dpProxyUrl by lazy { properties[Key("DP_PROXY_URL", stringType)] }
    val Configuration.dpProxyScope by lazy { properties[Key("DP_PROXY_SCOPE", stringType)] }

    val Configuration.dpProxyTokenProvider by lazy {
        val azureAd = OAuth2Config.AzureAd(properties)
        CachedOauth2Client(
            tokenEndpointUrl = azureAd.tokenEndpointUrl,
            authType = azureAd.clientSecret(),
        )
    }

    val pdlUrl by lazy { properties[Key("PDL_API_URL", stringType)] }
    val pdlAudience by lazy { properties[Key("PDL_AUDIENCE", stringType)] }
    val tokenXClient by lazy {
        val tokenX = OAuth2Config.TokenX(properties)
        CachedOauth2Client(
            tokenEndpointUrl = tokenX.tokenEndpointUrl,
            authType = tokenX.privateKey()
        )
    }
}
