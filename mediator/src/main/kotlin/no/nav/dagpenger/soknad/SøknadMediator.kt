package no.nav.dagpenger.soknad

import mu.KotlinLogging
import no.nav.dagpenger.soknad.db.LivsyklusRepository
import no.nav.dagpenger.soknad.db.SøknadMalRepository
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.withMDC

internal class SøknadMediator(
    rapidsConnection: RapidsConnection,
    private val livsyklusRepository: LivsyklusRepository,
    private val søknadMalRepository: SøknadMalRepository,
    private val personObservers: List<PersonObserver> = emptyList()
) : SøknadMalRepository by søknadMalRepository, LivsyklusRepository by livsyklusRepository {
    private companion object {
        val logger = KotlinLogging.logger { }
        val sikkerLogger = KotlinLogging.logger("tjenestekall")
    }

    private val behovMediator = BehovMediator(rapidsConnection, sikkerLogger)

    fun behandle(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        behandle(ønskeOmNySøknadHendelse) { person ->
            person.håndter(ønskeOmNySøknadHendelse)
        }
    }

    fun behandle(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        behandle(søknadOpprettetHendelse) { person ->
            person.håndter(søknadOpprettetHendelse)
        }
    }

    fun behandle(søknadInnsendtHendelse: SøknadInnsendtHendelse) {
        behandle(søknadInnsendtHendelse) { person ->
            person.håndter(søknadInnsendtHendelse)
        }
    }

    fun behandle(arkiverbarSøknadMottattHendelse: ArkiverbarSøknadMottattHendelse) {
        behandle(arkiverbarSøknadMottattHendelse) { person ->
            person.håndter(arkiverbarSøknadMottattHendelse)
        }
    }

    fun behandle(søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse) {
        behandle(søknadMidlertidigJournalførtHendelse) { person ->
            person.håndter(søknadMidlertidigJournalførtHendelse)
        }
    }

    fun behandle(journalførtHendelse: JournalførtHendelse) {
        behandle(journalførtHendelse) { person ->
            person.håndter(journalførtHendelse)
        }
    }

    private fun behandle(hendelse: Hendelse, håndter: (Person) -> Unit) {
        try {
            val person = hentEllerOpprettPerson(hendelse)
            personObservers.forEach { personObserver ->
                person.addObserver(personObserver)
            }
            håndter(person)
            finalize(person, hendelse)
        } catch (err: Aktivitetslogg.AktivitetException) {
            withMDC(kontekst(hendelse)) {
                logger.error("alvorlig feil i aktivitetslogg (se sikkerlogg for detaljer)")
            }
            withMDC(err.kontekst()) {
                sikkerLogger.error("alvorlig feil i aktivitetslogg: ${err.message}", err)
            }
        } catch (e: Exception) {
            errorHandler(e, e.message ?: "Ukjent feil")
        }
    }

    private fun kontekst(hendelse: Hendelse): Map<String, String> =
        when (hendelse) {
            is SøknadHendelse -> mapOf("søknad_uuid" to hendelse.søknadID().toString())
            is JournalførtHendelse -> mapOf("journalpostId" to hendelse.journalpostId())
            else -> {
                emptyMap()
            }
        }

    private fun errorHandler(err: Exception, message: String, context: Map<String, String> = emptyMap()) {
        logger.error("alvorlig feil: ${err.message} (se sikkerlogg for melding)", err)
        withMDC(context) { sikkerLogger.error("alvorlig feil: ${err.message}\n\t$message", err) }
    }

    private fun finalize(person: Person, hendelse: Hendelse) {
        lagre(person)
        if (!hendelse.hasMessages()) return
        if (hendelse.hasErrors()) return sikkerLogger.info("aktivitetslogg inneholder errors: ${hendelse.toLogString()}")
        sikkerLogger.info("aktivitetslogg inneholder meldinger: ${hendelse.toLogString()}")
        behovMediator.håndter(hendelse)
    }

    private fun hentEllerOpprettPerson(hendelse: Hendelse) =
        livsyklusRepository.hent(hendelse.ident()) ?: Person(hendelse.ident())
}
