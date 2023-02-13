package no.nav.dagpenger.soknad.utils

import mu.KotlinLogging
import no.nav.dagpenger.soknad.Configuration
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

interface Lock {
    fun withLock(id: UUID, waitTime: Int, leaseTime: Int, block: () -> Unit)
}

object HubbaLock : Lock {
    override fun withLock(id: UUID, waitTime: Int, leaseTime: Int, block: () -> Unit) = block()
}

class ReddissonLock(config: Config = defaultConfig) : Lock {
    companion object {
        val defaultConfig by lazy {
            Config().also {
                it.useSingleServer()
                    .setAddress("dp-soknad-redis:6379")
                    .setDnsMonitoring(true)
                    .setDnsMonitoringInterval(1000)
                    .password = Configuration.redisPassword
            }
        }
        private val logger = KotlinLogging.logger { }
    }

    val client: RedissonClient = Redisson.create(config)

    suspend fun hubba(id: UUID, waitTime: Int, leaseTime: Int, block: suspend () -> Unit) {
        client.getLock(id.toString()).let { lock ->
            lock.tryLock(waitTime.seconds.inWholeSeconds, leaseTime.seconds.inWholeSeconds, TimeUnit.SECONDS).let {
                when (it) {
                    true -> {
                        try {
                            block()
                        } finally {
                            kotlin.runCatching {
                                lock.unlock()
                            }.onFailure {
                                logger.warn(it) { "Kunne ikke release lock for $id" }
                            }
                        }
                    }

                    false -> {
                        throw RuntimeException("Fikk ikke tak i lock")
                    }
                }
            }
        }
    }

    override fun withLock(id: UUID, waitTime: Int, leaseTime: Int, block: () -> Unit) {
        client.getLock(id.toString()).let { lock ->
            lock.tryLock(waitTime.seconds.inWholeSeconds, leaseTime.seconds.inWholeSeconds, TimeUnit.SECONDS).let {
                when (it) {
                    true -> {
                        try {
                            block()
                        } finally {
                            kotlin.runCatching {
                                lock.unlock()
                            }.onFailure {
                                logger.warn(it) { "Kunne ikke release lock for $id" }
                            }
                        }
                    }

                    false -> {
                        throw RuntimeException("Fikk ikke tak i lock")
                    }
                }
            }
        }
    }
}
