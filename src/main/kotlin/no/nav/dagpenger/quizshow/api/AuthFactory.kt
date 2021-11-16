package no.nav.dagpenger.quizshow.api

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.getValue
import com.natpryce.konfig.stringType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import java.net.URL
import java.util.concurrent.TimeUnit

object AuthFactory {
    private object token_x : PropertyGroup() {
        val well_known_url by stringType
        val client_id by stringType
    }

    private val openIdConfiguration: AzureAdOpenIdConfiguration =
        runBlocking {
            httpClient.get<AzureAdOpenIdConfiguration>(Configuration.properties[token_x.well_known_url])
        }

    val clientId: String = Configuration.properties[token_x.client_id]
    val issuer = openIdConfiguration.issuer
    val jwkProvider: JwkProvider
        get() = JwkProviderBuilder(URL(openIdConfiguration.jwksUri))
            .cached(10, 24, TimeUnit.HOURS) // cache up to 10 JWKs for 24 hours
            .rateLimited(
                10,
                1,
                TimeUnit.MINUTES
            ) // if not cached, only allow max 10 different keys per minute to be fetched from external provider
            .build()
}

private data class AzureAdOpenIdConfiguration(
    @JsonProperty("jwks_uri")
    val jwksUri: String,
    @JsonProperty("issuer")
    val issuer: String,
    @JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String
)

private val httpClient = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = JacksonSerializer {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
}
