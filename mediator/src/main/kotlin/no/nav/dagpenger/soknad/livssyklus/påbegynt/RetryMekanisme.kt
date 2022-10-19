package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.prometheus.client.Histogram
import kotlinx.coroutines.delay
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Metrics.søknadDataRequests
import no.nav.dagpenger.soknad.Metrics.søknadDataResultat
import no.nav.dagpenger.soknad.Metrics.søknadDataTidBrukt

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
    søknadDataRequests.inc()
    val tidBrukt: Histogram.Timer = søknadDataTidBrukt.startTimer()
    repeat(times) {
        try {
            return block().also {
                logger.info { "Brukte $antallForsøk forsøk på henting av neste seksjon." }
                søknadDataResultat.labels(antallForsøk.toString()).inc()
                tidBrukt.observeDuration()
            }
        } catch (e: Exception) {
            logger.warn { "Forsøk: $antallForsøk/$times feilet på henting av neste seksjon. Prøver igjen om $currentDelay ms." }
            antallForsøk++
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return block().also { // last attempt
        logger.info { "Brukte $antallForsøk forsøk på henting av neste seksjon." }
        søknadDataResultat.labels(antallForsøk.toString()).inc()
        tidBrukt.observeDuration()
    }
}
