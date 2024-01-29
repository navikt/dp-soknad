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
            "PERSON_KONTO_REGISTER_URL" to "http://sokos-kontoregister-person.okonomi/api/borger/v1/hent-aktiv-konto",
        ),
    )
    val properties =
        ConfigurationProperties.systemProperties() overriding EnvironmentVariables() overriding defaultProperties
    val config: Map<String, String> = properties.list().reversed().fold(emptyMap()) { map, pair ->
        map + pair.second
    }
    val basePath = "/arbeid/dagpenger/soknadapi"
    val kontoRegisterUrl by lazy { properties[Key("PERSON_KONTO_REGISTER_URL", stringType)] }
    val kontoRegisterScope by lazy { properties[Key("PERSON_KONTO_REGISTER_SCOPE", stringType)] }
    val pdlUrl by lazy {
        properties[Key("PDL_API_HOST", stringType)].let {
            "https://$it/graphql"
        }
    }
    val pdlAudience by lazy { properties[Key("PDL_AUDIENCE", stringType)] }
    val dpInnsynAudience by lazy { properties[Key("DP_INNSYN_AUDIENCE", stringType)] }
    val dpInnsynUrl by lazy { properties[Key("DP_INNSYN_URL", stringType)] }

    val aaregUrl by lazy {
        properties[Key("AAREG_API_HOST", stringType)].let {
            "https://$it"
        }
    }

    val aaregAudience by lazy { properties[Key("AAREG_AUDIENCE", stringType)] }

    val eregUrl by lazy {
        properties[Key("EREG_API_HOST", stringType)].let {
            "https://$it"
        }
    }

    val tokenXClient by lazy {
        val tokenX = OAuth2Config.TokenX(properties)
        CachedOauth2Client(
            tokenEndpointUrl = tokenX.tokenEndpointUrl,
            authType = tokenX.privateKey(),
        )
    }

    fun tokenXClient(audience: String) = { subjectToken: String ->
        tokenXClient.tokenExchange(
            token = subjectToken,
            audience = audience,
        ).accessToken
    }
}
