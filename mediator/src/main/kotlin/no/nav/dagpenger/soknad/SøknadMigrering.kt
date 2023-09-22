package no.nav.dagpenger.soknad

import mu.KotlinLogging
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.mal.SøknadMal
import no.nav.dagpenger.soknad.mal.SøknadMalRepository
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import java.util.UUID

class SøknadMigrering constructor(
    private val søknadRepository: SøknadRepository,
    søknadMalRepository: SøknadMalRepository,
    private val rapid: RapidsConnection,
) {
    init {
        søknadMalRepository.addObserver(::migrer)
    }

    private fun migrer(søknadMal: SøknadMal) {
        logger.info { "Oppdaget ny søknadmal for prosessversjon=${søknadMal.prosessversjon}" }
        val søknader = søknadRepository.hentPåbegynteSøknader(søknadMal.prosessversjon)

        logger.info { "Fant ${søknader.size} søknader som skal migreres" }

        søknader.forEach {
            rapid.publish(MigreringsBehov(it.søknadUUID(), it.ident()).asMessage().toJson())
            logger.info { "Publiserer behov for Migrering for søknadId=$it." }
        }
    }

    private data class MigreringsBehov(private val søknadUUID: UUID, private val ident: String) {
        fun asMessage() = JsonMessage.newNeed(
            listOf(
                "MigrerProsess",
            ),
            mapOf(
                "søknad_uuid" to søknadUUID.toString(),
                "ident" to ident,
            ),
        )
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
