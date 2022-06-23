package no.nav.dagpenger.søknad

import mu.KotlinLogging
import no.nav.dagpenger.søknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.søknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.søknad.hendelse.HarPåbegyntSøknadHendelse
import no.nav.dagpenger.søknad.hendelse.Hendelse
import no.nav.dagpenger.søknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.søknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.søknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.søknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.søknad.livssyklus.LivssyklusRepository
import no.nav.dagpenger.søknad.livssyklus.ferdigstilling.FerdigstiltSøknadRepository
import no.nav.dagpenger.søknad.livssyklus.påbegynt.FaktumSvar
import no.nav.dagpenger.søknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.søknad.livssyklus.påbegynt.SøknadCacheRepository
import no.nav.dagpenger.søknad.mal.SøknadMalRepository
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.withMDC
import java.util.UUID

internal class SøknadMediator(
    private val rapidsConnection: RapidsConnection,
    private val søknadCacheRepository: SøknadCacheRepository,
    private val livssyklusRepository: LivssyklusRepository,
    private val søknadMalRepository: SøknadMalRepository,
    private val ferdigstiltSøknadRepository: FerdigstiltSøknadRepository,
    private val personObservers: List<PersonObserver> = emptyList()
) : SøknadCacheRepository by søknadCacheRepository,
    SøknadMalRepository by søknadMalRepository,
    LivssyklusRepository by livssyklusRepository,
    FerdigstiltSøknadRepository by ferdigstiltSøknadRepository {
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

    fun behandle(harPåbegyntSøknadHendelse: HarPåbegyntSøknadHendelse) {
        behandle(harPåbegyntSøknadHendelse) { person ->
            person.håndter(harPåbegyntSøknadHendelse)
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

    fun behandle(slettSøknadHendelse: SlettSøknadHendelse) {
        behandle(slettSøknadHendelse) { person ->
            person.håndter(slettSøknadHendelse)
        }
    }

    fun behandle(faktumSvar: FaktumSvar) {
        val faktumOppdatertHendelse = FaktumOppdatertHendelse(faktumSvar.søknadUuid(), faktumSvar.eier())
        behandle(faktumOppdatertHendelse) { person ->
            person.håndter(faktumOppdatertHendelse)
            søknadCacheRepository.slett(faktumSvar.søknadUuid(), faktumSvar.eier())
            rapidsConnection.publish(faktumSvar.toJson())
        }
        logger.info { "Sendte faktum svar for ${faktumSvar.søknadUuid()}" }
    }

    fun behandle(søkerOppgave: SøkerOppgave) {
        søknadCacheRepository.lagre(søkerOppgave)
    }

    internal fun hentEllerOpprettSøknadsprosess(ident: String, språk: String): Søknadsprosess {
        return Søknadsprosess.NySøknadsProsess().also {
            behandle(ØnskeOmNySøknadHendelse(it.getSøknadsId(), språk, ident))
        }
        // return hentPåbegynte(ident).singleOrNull()?.let {
        //     PåbegyntSøknadsProsess(it.uuid).also {
        //         behandle(HarPåbegyntSøknadHendelse(ident, it.getSøknadsId()))
        //     }
        // } ?: Søknadsprosess.NySøknadsProsess().also {
        //     behandle(ØnskeOmNySøknadHendelse(ident, it.getSøknadsId()))
        // }
    }

    private fun behandle(hendelse: Hendelse, håndter: (Person) -> Unit) =
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
            throw err
        } catch (e: Exception) {
            errorHandler(e, e.message ?: "Ukjent feil")
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
        livssyklusRepository.hent(hendelse.ident()) ?: Person(hendelse.ident())
}

internal sealed class Søknadsprosess {
    abstract fun getSøknadsId(): UUID

    internal class PåbegyntSøknadsProsess(private val søknadID: UUID) : Søknadsprosess() {
        override fun getSøknadsId(): UUID = søknadID
    }

    internal class NySøknadsProsess : Søknadsprosess() {
        private val søknadID = UUID.randomUUID()
        override fun getSøknadsId(): UUID = søknadID
    }
}
