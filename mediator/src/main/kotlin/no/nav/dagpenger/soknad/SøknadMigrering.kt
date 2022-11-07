package no.nav.dagpenger.soknad

import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.mal.SøknadMal
import no.nav.dagpenger.soknad.mal.SøknadMalRepository
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import java.util.UUID

class SøknadMigrering constructor(
    private val søknadRepository: SøknadRepository,
    søknadMalRepository: SøknadMalRepository,
    private val rapid: RapidsConnection
) {

    init {
        søknadMalRepository.addObserver { søknadMal -> migrer(søknadMal) }
    }

    private fun migrer(søknadMal: SøknadMal) {
        val søknader = søknadRepository.hentPåbegynteSøknader(søknadMal)

        søknader.forEach {
            rapid.publish(MigreringsBehov(it.søknadUUID(), it.ident()).asMessage().toString())
        }
    }

    private data class MigreringsBehov(private val søknadUUID: UUID, private val ident: String) {

        fun asMessage() = JsonMessage.newNeed(
            listOf(
                "MigrerProsess"
            ), mapOf(
                "søknad_uuid" to søknadUUID.toString(),
                "ident" to ident
            )
        )
    }
}
