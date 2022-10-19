package no.nav.dagpenger.soknad.livssyklus.påbegynt

import kotlinx.coroutines.delay
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Metrics.søknadDataRetries

private val logger = KotlinLogging.logger {}

suspend fun <T> retryIO(
    times: Int,
    initialDelay: Long = 100, // 0.1 second
    maxDelay: Long = 1000, // 1 second
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    var antallForsøk = 1
    repeat(times) {
        try {
            return block().also {
                logger.info { "Brukte $antallForsøk forsøk på henting av neste seksjon." }
            }
        } catch (e: Exception) {
            logger.warn { "Forsøk: $antallForsøk/$times på henting av neste seksjon." }
        } finally {
            søknadDataRetries.labels(antallForsøk.toString()).inc()
            antallForsøk++
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block().also { // last attempt
        logger.info { "Brukte $antallForsøk forsøk på henting av neste seksjon." }
    }
}
