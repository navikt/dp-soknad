package no.nav.dagpenger.soknad.utils

import mu.KotlinLogging
import no.nav.dagpenger.soknad.Configuration
import org.redisson.Redisson
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
                    .password = Configuration.redisPassword
            }
        }
        private val logger = KotlinLogging.logger { }
    }

    private val client = Redisson.create(config)

    override fun withLock(id: UUID, waitTime: Int, leaseTime: Int, block: () -> Unit) {
        client.getLock(id.toString()).let { lock ->
            lock.tryLock(waitTime.seconds.inWholeSeconds, leaseTime.seconds.inWholeSeconds, TimeUnit.SECONDS).let {
                when (it) {
                    true -> {
                        try {
                            block()
                        } finally {
                            try {
                                lock.unlock()
                            } catch (t: Throwable) {
                                logger.warn(t) {
                                    "fikk ikke releaset lock"
                                }
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
