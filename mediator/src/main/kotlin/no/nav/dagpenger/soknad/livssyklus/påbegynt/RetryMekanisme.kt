package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.prometheus.client.Histogram
import kotlinx.coroutines.delay
import mu.KotlinLogging
import no.nav.dagpenger.soknad.db.SøkerOppgaveNotFoundException
import no.nav.dagpenger.soknad.monitoring.Metrics
import no.nav.dagpenger.soknad.monitoring.Metrics.søknadDataRequests
import no.nav.dagpenger.soknad.monitoring.Metrics.søknadDataResultat
import no.nav.dagpenger.soknad.monitoring.Metrics.søknadDataTidBrukt

private val logger = KotlinLogging.logger {}

suspend fun <T> retryIO(
    times: Int,
    // 0.1 second
    initialDelay: Long = 100,
    // 1 second
    maxDelay: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> T,
): T {
    var currentDelay = initialDelay
    var antallForsøk = 1
    søknadDataRequests.inc()
    val tidBrukt: Histogram.Timer = søknadDataTidBrukt.startTimer()
    repeat(times) {
        try {
            return block().also {
                val faktiskTid: Double = tidBrukt.observeDuration()
                logger.info { "Brukte $antallForsøk forsøk og $faktiskTid sekund på henting av neste seksjon." }
                søknadDataResultat.labels(antallForsøk.toString()).inc()
            }
        } catch (e: Exception) {
            antallForsøk++
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return try {
        block().also { // last attempt
            val faktiskTid: Double = tidBrukt.observeDuration()
            logger.info { "Brukte $antallForsøk forsøk og $faktiskTid sekund på henting av neste seksjon." }
            søknadDataResultat.labels(antallForsøk.toString()).inc()
        }
    } catch (e: SøkerOppgaveNotFoundException) {
        logger.info { "Brukte $antallForsøk forsøk uten å få hentet neste seksjon." }
        Metrics.søknadDataTimeouts.inc()
        throw e
    }
}
