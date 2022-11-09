package no.nav.dagpenger.soknad

import mu.KotlinLogging
import no.nav.dagpenger.soknad.db.SøknadDataRepository
import no.nav.dagpenger.soknad.hendelse.ArkiverbarSøknadMottattHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.HarPåbegyntSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.InnsendingMetadataMottattHendelse
import no.nav.dagpenger.soknad.hendelse.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadMidlertidigJournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.livssyklus.ferdigstilling.FerdigstiltSøknadRepository
import no.nav.dagpenger.soknad.livssyklus.påbegynt.FaktumSvar
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.soknad.mal.SøknadMalRepository
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.withMDC
import java.util.UUID

internal class SøknadMediator(
    private val rapidsConnection: RapidsConnection,
    private val søknadDataRepository: SøknadDataRepository,
    private val søknadMalRepository: SøknadMalRepository,
    private val ferdigstiltSøknadRepository: FerdigstiltSøknadRepository,
    private val søknadRepository: SøknadRepository,
    private val søknadObservers: List<SøknadObserver> = emptyList()
) : SøknadDataRepository by søknadDataRepository,
    SøknadMalRepository by søknadMalRepository,
    FerdigstiltSøknadRepository by ferdigstiltSøknadRepository,
    SøknadRepository by søknadRepository {
    private companion object {
        val logger = KotlinLogging.logger { }
        val sikkerLogger = KotlinLogging.logger("tjenestekall")
    }

    private val behovMediator = BehovMediator(rapidsConnection, sikkerLogger)

    fun behandle(ønskeOmNySøknadHendelse: ØnskeOmNySøknadHendelse) {
        behandle(ønskeOmNySøknadHendelse) { søknad ->
            søknad.håndter(ønskeOmNySøknadHendelse)
        }
    }

    fun behandle(harPåbegyntSøknadHendelse: HarPåbegyntSøknadHendelse) {
        behandle(harPåbegyntSøknadHendelse) { søknad ->
            søknad.håndter(harPåbegyntSøknadHendelse)
        }
    }

    fun behandle(søknadOpprettetHendelse: SøknadOpprettetHendelse) {
        behandle(søknadOpprettetHendelse) { søknad ->
            søknad.håndter(søknadOpprettetHendelse)
        }
    }

    fun behandle(søknadInnsendtHendelse: SøknadInnsendtHendelse) {
        behandle(søknadInnsendtHendelse) { søknad ->
            søknad.håndter(søknadInnsendtHendelse)
        }
    }

    fun behandle(arkiverbarSøknadMottattHendelse: ArkiverbarSøknadMottattHendelse) {
        behandle(arkiverbarSøknadMottattHendelse) { søknad ->
            søknad.håndter(arkiverbarSøknadMottattHendelse)
        }
    }

    fun behandle(søknadMidlertidigJournalførtHendelse: SøknadMidlertidigJournalførtHendelse) {
        behandle(søknadMidlertidigJournalførtHendelse) { søknad ->
            søknad.håndter(søknadMidlertidigJournalførtHendelse)
        }
    }

    fun behandle(journalførtHendelse: JournalførtHendelse) {
        behandle(journalførtHendelse) { søknad ->
            søknad.håndter(journalførtHendelse)
        }
    }

    fun behandle(slettSøknadHendelse: SlettSøknadHendelse) {
        behandle(slettSøknadHendelse) { søknad ->
            søknad.håndter(slettSøknadHendelse)
        }
    }

    fun behandle(faktumSvar: FaktumSvar) {
        val faktumOppdatertHendelse = FaktumOppdatertHendelse(faktumSvar.søknadUuid(), faktumSvar.eier())
        behandle(faktumOppdatertHendelse) { person ->
            person.håndter(faktumOppdatertHendelse)
            rapidsConnection.publish(faktumSvar.toJson())
        }
        logger.info { "Sendte faktum svar for ${faktumSvar.søknadUuid()}" }
    }

    fun behandle(søkerOppgave: SøkerOppgave) {
        val søkeroppgaveHendelse =
            SøkeroppgaveHendelse(søkerOppgave.søknadUUID(), søkerOppgave.eier(), søkerOppgave.sannsynliggjøringer())
        behandle(søkeroppgaveHendelse) { person ->
            person.håndter(søkeroppgaveHendelse)
            søknadDataRepository.lagre(søkerOppgave)
        }
    }

    fun behandle(hendelse: DokumentasjonIkkeTilgjengelig) {
        behandle(hendelse) { søknad ->
            søknad.håndter(hendelse)
        }
    }

    fun behandle(hendelse: LeggTilFil) {
        behandle(hendelse) { søknad ->
            søknad.håndter(hendelse)
        }
    }

    fun behandle(hendelse: SlettFil) {
        behandle(hendelse) { søknad ->
            søknad.håndter(hendelse)
        }
    }

    fun behandle(hendelse: DokumentKravSammenstilling) {
        behandle(hendelse) { søknad ->
            søknad.håndter(hendelse)
        }
    }

    fun behandle(innsendingMetadataMottattHendelse: InnsendingMetadataMottattHendelse) {
        behandle(innsendingMetadataMottattHendelse) { søknad ->
            søknad.håndter(innsendingMetadataMottattHendelse)
        }
    }

    fun behandle(hendelse: MigrertProsessHendelse){
        behandle(hendelse) { søknad ->
            søknad.håndter(hendelse)
        }
    }

    internal fun opprettSøknadsprosess(
        ident: String,
        språk: String,
        prosessnavn: Prosessnavn
    ) = Søknadsprosess.NySøknadsProsess().also {
        behandle(ØnskeOmNySøknadHendelse(it.getSøknadsId(), ident, språk, prosessnavn))
    }

    private fun behandle(hendelse: SøknadHendelse, håndter: (Søknad) -> Unit) = try {
        val søknad = hentEllerOpprettSøknad(hendelse)
        søknadObservers.forEach { søknadObserver ->
            søknad.addObserver(søknadObserver)
        }
        håndter(søknad)
        lagre(søknad)
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

    private fun hentEllerOpprettSøknad(hendelse: SøknadHendelse): Søknad {
        val søknad = hent(hendelse.søknadID())
        return when (hendelse) {
            is ØnskeOmNySøknadHendelse -> søknad ?: Søknad(hendelse.søknadID(), hendelse.språk(), hendelse.ident())
            else -> søknad ?: hendelse.severe("Søknaden finnes ikke")
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

    private fun finalize(hendelse: Hendelse) {
        if (!hendelse.hasMessages()) return
        if (hendelse.hasErrors()) return sikkerLogger.info("aktivitetslogg inneholder errors: ${hendelse.toLogString()}")
        sikkerLogger.info("aktivitetslogg inneholder meldinger: ${hendelse.toLogString()}")
        behovMediator.håndter(hendelse)
    }
}

enum class Prosesstype {
    Søknad, Innsending
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
