package no.nav.dagpenger.soknad.utils

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

class ReddissonLock() : Lock {
    private val client = Redisson.create(
        Config().also {
            it.useSingleServer()
                .setAddress("dp-soknad-redis:6379")
                .password = Configuration.redisPassword
        }
    )

    override fun withLock(id: UUID, waitTime: Int, leaseTime: Int, block: () -> Unit) {
        client.getLock(id.toString()).let { lock ->
            lock
                .tryLock(waitTime.seconds.inWholeSeconds, leaseTime.seconds.inWholeSeconds, TimeUnit.SECONDS).let {
                    when (it) {
                        true -> {
                            try {
                                block()
                            } finally {
                                lock.unlock()
                                lock.deleteAsync()
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
