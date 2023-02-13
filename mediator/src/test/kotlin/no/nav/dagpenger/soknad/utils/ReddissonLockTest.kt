package no.nav.dagpenger.soknad.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.Test
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import java.util.UUID

class ReddissonLockTest {
    private val redis by lazy {
        GenericContainer<Nothing>("bitnami/redis:6.2").apply {
            withEnv("REDIS_PASSWORD", "redis")
            withExposedPorts(6379)
            start()
        }
    }

    val logger = KotlinLogging.logger { }

    @Test
    fun hubba() {
        val reddissonLock = ReddissonLock(
            config = Config().also {
                it.useSingleServer()
                    .setAddress("${redis.host}:${redis.firstMappedPort}")
                    .password = "redis"
            }
        )
        val id = UUID.randomUUID().also { println(it) }

        runBlocking {
            launch {
                logger.info("hubba launch")
                reddissonLock.hubba(id, 1, 10) {
                    reddissonLock.client.keys.also { println(it.keys.map { s -> s }) }
                    delay(1000)
                    logger.info("hubba done")
                }
            }

            launch {
                logger.info("bubba launch")
                reddissonLock.hubba(id, 1, 10) {
                    delay(1000)
                    reddissonLock.client.keys.also { println(it.keys.map { s -> s }) }
                    logger.info("bubba done")
                }
            }
        }
        reddissonLock.client.keys.also { println(it.keys.map { s -> s }) }
    }
}
