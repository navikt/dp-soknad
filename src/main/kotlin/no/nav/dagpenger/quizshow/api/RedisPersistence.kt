package no.nav.dagpenger.quizshow.api

import io.lettuce.core.RedisClient

class RedisPersistence(redisHost: String, redisPassword: String) : Persistence {
    private val redisClient = RedisClient.create("redis://$redisPassword@$redisHost")

    override fun lagre(key: String, value: String) {
        redisClient.connect().use { connection ->
            connection.sync().set(key, value)
        }
    }

    override fun hent(key: String): String? {
        return redisClient.connect().use { connection ->
            connection.sync().get(key)
        }
    }
}
