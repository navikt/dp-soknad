package no.nav.dagpenger.soknad

import com.natpryce.konfig.ConfigurationMap
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.Key
import com.natpryce.konfig.overriding
import com.natpryce.konfig.stringType
import no.nav.dagpenger.oauth2.CachedOauth2Client
import no.nav.dagpenger.oauth2.OAuth2Config

internal object Configuration {

    const val appName = "dp-soknad"

    private val defaultProperties = ConfigurationMap(
        mapOf(
            "RAPID_APP_NAME" to "dp-soknad",
            "KAFKA_CONSUMER_GROUP_ID" to "dp-soknad-v1",
            "KAFKA_RAPID_TOPIC" to "teamdagpenger.rapid.v1",
            "KAFKA_EXTRA_TOPIC" to "teamdagpenger.journalforing.v1",
            "KAFKA_RESET_POLICY" to "latest",
        )
    )
    val properties =
        ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties

    val config: Map<String, String> = properties.list().reversed().fold(emptyMap()) { map, pair ->
        map + pair.second
    }

    val basePath = "/dagpenger/soknadapi"

    val dpProxyUrl by lazy { properties[Key("DP_PROXY_URL", stringType)] }
    val dpProxyScope by lazy { properties[Key("DP_PROXY_SCOPE", stringType)] }

    val dpProxyTokenProvider by lazy {
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
