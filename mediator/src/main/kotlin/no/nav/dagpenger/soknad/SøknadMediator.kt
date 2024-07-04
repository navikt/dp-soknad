package no.nav.dagpenger.soknad

import mu.KotlinLogging
import no.nav.dagpenger.soknad.db.DokumentkravRepository
import no.nav.dagpenger.soknad.db.SøknadDataRepository
import no.nav.dagpenger.soknad.hendelse.DokumentKravHendelse
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.FaktumOppdatertHendelse
import no.nav.dagpenger.soknad.hendelse.HarPåbegyntSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.MigrertProsessHendelse
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadOpprettetHendelse
import no.nav.dagpenger.soknad.hendelse.innsending.JournalførtHendelse
import no.nav.dagpenger.soknad.hendelse.ØnskeOmNySøknadHendelse
import no.nav.dagpenger.soknad.livssyklus.SøknadRepository
import no.nav.dagpenger.soknad.livssyklus.ferdigstilling.FerdigstiltSøknadRepository
import no.nav.dagpenger.soknad.livssyklus.påbegynt.FaktumSvar
import no.nav.dagpenger.soknad.livssyklus.påbegynt.SøkerOppgave
import no.nav.dagpenger.soknad.mal.SøknadMalRepository
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.withMDC
import java.time.LocalDateTime
import java.util.UUID

internal class SøknadMediator(
    private val rapidsConnection: RapidsConnection,
    private val søknadDataRepository: SøknadDataRepository,
    private val søknadMalRepository: SøknadMalRepository,
    private val ferdigstiltSøknadRepository: FerdigstiltSøknadRepository,
    private val søknadRepository: SøknadRepository,
    private val dokumentkravRepository: DokumentkravRepository,
    private val søknadObservers: List<SøknadObserver> = emptyList(),
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

    fun behandle(slettSøknadHendelse: SlettSøknadHendelse) {
        behandle(slettSøknadHendelse) { søknad ->
            søknad.håndter(slettSøknadHendelse)
        }
    }

    fun behandle(faktumSvar: FaktumSvar) {
        val faktumOppdatertHendelse = FaktumOppdatertHendelse(faktumSvar.søknadUuid(), faktumSvar.ident())
        behandle(faktumOppdatertHendelse) { person ->
            person.håndter(faktumOppdatertHendelse)
            rapidsConnection.publish(faktumSvar.ident(), faktumSvar.toJson())
        }
        logger.info { "Sendte faktum svar for ${faktumSvar.søknadUuid()}" }
    }

    fun behandle(søkerOppgave: SøkerOppgave) {
        val hendelse = søkerOppgave.hendelse().apply {
            addObserver {
                // Lagre oppgaven hver gang modellen har håndtert hendelsen
                søknadDataRepository.lagre(søkerOppgave)
            }
        }
        behandle(hendelse) { søknad ->
            søknad.håndter(hendelse)
        }
    }

    private fun behandleDokumentasjonkravHendelse(
        hendelse: DokumentKravHendelse,
        block: (repository: DokumentkravRepository) -> Unit,
    ) {
        block(dokumentkravRepository)
        finalize(hendelse)
    }

    fun behandle(hendelse: DokumentasjonIkkeTilgjengelig) {
        behandleDokumentasjonkravHendelse(hendelse) { repository ->
            repository.håndter(hendelse)
        }
    }

    fun behandle(hendelse: LeggTilFil) {
        behandleDokumentasjonkravHendelse(hendelse) { repository ->
            repository.håndter(hendelse)
        }
    }

    fun behandle(hendelse: SlettFil) {
        behandleDokumentasjonkravHendelse(hendelse) { repository ->
            repository.håndter(hendelse)
        }
    }

    fun behandle(hendelse: DokumentKravSammenstilling) {
        behandleDokumentasjonkravHendelse(hendelse) { repository ->
            repository.håndter(hendelse)
            hendelse.behov(
                Aktivitetslogg.Aktivitet.Behov.Behovtype.DokumentkravSvar,
                "Må svare dokumentkravet i Quiz",
                mapOf(
                    "id" to hendelse.kravId,
                    "type" to "dokument",
                    "urn" to hendelse.urn().toString(),
                    "lastOppTidsstempel" to LocalDateTime.now(),
                ),
            )
        }
    }

    fun behandle(hendelse: MigrertProsessHendelse, søkerOppgave: SøkerOppgave) {
        behandle(hendelse) { søknad ->
            søknad.håndter(hendelse)
            søknadDataRepository.lagre(søkerOppgave)
        }
    }

    internal fun opprettSøknadsprosess(
        ident: String,
        språk: String,
        prosessnavn: Prosessnavn,
        søknadId: UUID,
    ) = Søknadsprosess.NySøknadsProsess(søknadId).also {
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
            is ØnskeOmNySøknadHendelse -> søknad ?: søknadRepository.opprett(
                hendelse.søknadID(),
                hendelse.språk(),
                hendelse.ident(),
            )

            else -> søknad ?: throw SøknadIkkeFunnet("Søknaden med id ${hendelse.søknadID()} finnes ikke")
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

    internal class SøknadIkkeFunnet(message: String) : RuntimeException(message)
}

enum class Prosesstype {
    Søknad, Innsending
}

internal sealed class Søknadsprosess {
    abstract fun getSøknadsId(): UUID

    internal class NySøknadsProsess(søknadId: UUID) : Søknadsprosess() {
        private val søknadID = søknadId
        override fun getSøknadsId(): UUID = søknadID
    }
}
