package no.nav.dagpenger.quizshow.api

import io.lettuce.core.RedisClient

class RedisPersistence(redisHost: String, redisPassword: String) : Persistence {
    private val redisConnection = RedisClient.create("redis://$redisPassword@$redisHost").connect()

    override fun lagre(key: String, value: String) {
        redisConnection.sync().set(key, value)
    }

    override fun hent(key: String): String? {
        return redisConnection.sync().get(key)
    }

    override fun close() {
        redisConnection.close()
    }
}
