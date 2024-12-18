package no.nav.dagpenger.soknad.innsending

import mu.KotlinLogging
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.BehovMediator
import no.nav.dagpenger.soknad.Innsending
import no.nav.dagpenger.soknad.InnsendingObserver
import no.nav.dagpenger.soknad.db.DataConstraintException
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingMetadataMottattHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingPåminnelseHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.innsending.meldinger.NyInnsendingHendelse
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.withMDC

internal class InnsendingMediator(
    rapidsConnection: RapidsConnection,
    private val innsendingRepository: InnsendingRepository,
    private val innsendingObservers: List<InnsendingObserver> = emptyList(),
) : InnsendingRepository by innsendingRepository {
    private companion object {
        val logger = KotlinLogging.logger { }
        val sikkerLogger = KotlinLogging.logger("tjenestekall")
    }

    private val behovMediator = BehovMediator(rapidsConnection, sikkerLogger)

    fun behandle(nyInnsendingHendelse: NyInnsendingHendelse) {
        behandle(nyInnsendingHendelse) { innsending ->
            innsending.håndter(nyInnsendingHendelse)
        }
    }

    fun behandle(innsendingMetadataMottattHendelse: InnsendingMetadataMottattHendelse) {
        behandle(innsendingMetadataMottattHendelse) { innsending ->
            innsending.håndter(innsendingMetadataMottattHendelse)
        }
    }

    fun behandle(arkiverbarSøknadMottattHendelse: ArkiverbarSøknadMottattHendelse) {
        behandle(arkiverbarSøknadMottattHendelse) { innsending ->
            innsending.håndter(arkiverbarSøknadMottattHendelse)
        }
    }

    fun behandle(søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse) {
        behandle(søknadMidlertidigJournalførtHendelse) { innsending ->
            innsending.håndter(søknadMidlertidigJournalførtHendelse)
        }
    }

    fun behandle(journalførtHendelse: JournalførtHendelse) {
        behandle(journalførtHendelse) { innsending ->
            innsending.håndter(journalførtHendelse)
        }
    }

    fun behandle(innsendingPåminnelseHendelse: InnsendingPåminnelseHendelse) {
        behandle(innsendingPåminnelseHendelse) { innsending ->
            innsending.håndter(innsendingPåminnelseHendelse)
        }
    }

    private fun behandle(
        hendelse: Hendelse,
        innsending: Innsending,
        håndter: (Innsending) -> Unit,
    ) = try {
        håndter(innsending)
        innsendingRepository.lagre(innsending)
        finalize(hendelse)
    } catch (err: Aktivitetslogg.AktivitetException) {
        withMDC(kontekst(hendelse)) {
            logger.error("alvorlig feil i aktivitetslogg (se sikkerlogg for detaljer)")
        }
        withMDC(err.kontekst()) {
            sikkerLogger.error("alvorlig feil i aktivitetslogg: ${err.message}", err)
        }
        throw err
    } catch (e: Exception) {
        errorHandler(e, e.message ?: "Ukjent feil")
        throw e
    }

    private fun behandle(
        hendelse: JournalførtHendelse,
        håndter: (Innsending) -> Unit,
    ) {
        try {
            val innsending =
                innsendingRepository.hentInnsending(hendelse.journalpostId()).also { innsending ->
                    innsendingObservers.forEach { innsending.addObserver(it) }
                }
            behandle(hendelse, innsending, håndter)
        } catch (e: DataConstraintException) {
            logger.info { "Fant ikke innsending basert på journalpost: ${hendelse.journalpostId()}" }
        }
    }

    private fun behandle(
        hendelse: InnsendingHendelse,
        håndter: (Innsending) -> Unit,
    ) {
        val innsending =
            hentEllerOpprettInnsending(hendelse).also { innsending ->
                innsendingObservers.forEach { innsending.addObserver(it) }
            }
        behandle(hendelse, innsending, håndter)
    }

    private fun hentEllerOpprettInnsending(hendelse: InnsendingHendelse): Innsending {
        val innsending = innsendingRepository.hent(hendelse.innsendingId())
        return when (hendelse) {
            is NyInnsendingHendelse -> hendelse.innsending
            else -> innsending ?: throw SøknadIkkeFunnet("Innsending med id ${hendelse.innsendingId()} finnes ikke")
        }
    }

    private fun kontekst(hendelse: Hendelse): Map<String, String> =
        when (hendelse) {
            is SøknadHendelse -> mapOf("søknad_uuid" to hendelse.søknadID().toString())
            is InnsendingHendelse -> mapOf("innsendingId" to hendelse.innsendingId().toString())
            is JournalførtHendelse -> mapOf("journalpostId" to hendelse.journalpostId())
            else -> {
                emptyMap()
            }
        }

    private fun errorHandler(
        err: Exception,
        message: String,
        context: Map<String, String> = emptyMap(),
    ) {
        logger.error("alvorlig feil: ${err.message} (se sikkerlogg for melding)", err)
        withMDC(context) { sikkerLogger.error("alvorlig feil: ${err.message}\n\t$message", err) }
    }

    private fun finalize(hendelse: Hendelse) {
        if (!hendelse.hasMessages()) return
        if (hendelse.hasErrors()) return sikkerLogger.info("aktivitetslogg inneholder errors: ${hendelse.toLogString()}")
        sikkerLogger.info("aktivitetslogg inneholder meldinger: ${hendelse.toLogString()}")
        behovMediator.håndter(hendelse)
    }

    internal class SøknadIkkeFunnet(
        message: String,
    ) : RuntimeException(message)
}
