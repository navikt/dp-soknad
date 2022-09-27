package no.nav.dagpenger.soknad.utils.auth

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import mu.KotlinLogging
import no.nav.dagpenger.soknad.IkkeTilgangExeption
import no.nav.dagpenger.soknad.SøknadMediator
import java.util.UUID

internal class SøknadEierValidator(private val mediator: SøknadMediator) {
    companion object {

        private val sikkerLogger = KotlinLogging.logger("tjenestekall")
        private val cache: Cache<UUID, String> = Caffeine.newBuilder()
            .maximumSize(10000)
            .recordStats()
            .build<UUID, String>()
            .also {
                CaffeineCacheMetrics.monitor(Metrics.globalRegistry, it, "SoknadEier");
            }
    }

    fun erEier(søknadId: UUID, forventetEier: String): Boolean {
        val faktiskEier = requireNotNull(cache.get(søknadId, mediator::hentEier)) {
            "Fant ikke søknad: $søknadId"
        }
        return sjekkEier(faktiskEier, forventetEier)
    }

    fun valider(søknadId: UUID, forventetEier: String) {
        if (!erEier(søknadId, forventetEier)) {
            sikkerLogger.error { "Bruker $forventetEier har ikke tilgang til søknad $søknadId" }
            throw IkkeTilgangExeption("Ikke eier")
        }
    }

    private fun sjekkEier(faktiskEier: String, forventetEier: String) =
        faktiskEier.equals(forventetEier, true)
}
