package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.JsonNode
import io.lettuce.core.RedisClient
import no.nav.dagpenger.quizshow.api.Persistence
import no.nav.dagpenger.quizshow.api.serder.objectMapper

class RedisPersistence(redisHost: String, redisPassword: String) : Persistence {
    private val redisConnection = RedisClient.create("redis://$redisPassword@$redisHost").connect()

    override fun lagre(key: String, value: String) {
        redisConnection.sync().set(key, value)
    }

    override fun hent(key: String): JsonNode? {
        return objectMapper.readTree(redisConnection.sync().get(key))
    }

    override fun close() {
        redisConnection.close()
    }
}
