package no.nav.dagpenger.soknad.dokumentasjonskrav

import mu.KotlinLogging
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.BehovMediator
import no.nav.dagpenger.soknad.db.DokumentkravRepository
import no.nav.dagpenger.soknad.hendelse.DokumentKravSammenstilling
import no.nav.dagpenger.soknad.hendelse.DokumentasjonIkkeTilgjengelig
import no.nav.dagpenger.soknad.hendelse.Hendelse
import no.nav.dagpenger.soknad.hendelse.LeggTilFil
import no.nav.dagpenger.soknad.hendelse.SlettFil
import no.nav.dagpenger.soknad.hendelse.SøkeroppgaveHendelse
import no.nav.dagpenger.soknad.hendelse.SøknadInnsendtHendelse
import no.nav.helse.rapids_rivers.RapidsConnection
import java.time.LocalDateTime

internal class DokumentkravMediator(
    rapidsConnection: RapidsConnection,
    private val repository: DokumentkravRepository
) : DokumentkravRepository by repository {
    private companion object {
        val logger = KotlinLogging.logger { }
        val sikkerLogger = KotlinLogging.logger("tjenestekall.DokumentkravMediator")
    }

    private val behovMediator = BehovMediator(rapidsConnection, sikkerLogger)

    fun håndter(hendelse: SøkeroppgaveHendelse) {
        repository.hent(hendelse.søknadID()).let { dokumentkrav ->
            dokumentkrav.håndter(hendelse.sannsynliggjøringer())
            repository.lagre(hendelse.søknadID(), dokumentkrav)
        }
    }

    fun håndter(hendelse: SøknadInnsendtHendelse) {
        repository.hent(søknadId = hendelse.søknadID()).let {
            it.håndter(hendelse)
            repository.lagre(hendelse.søknadID(), it)
        }
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
                    "lastOppTidsstempel" to LocalDateTime.now()
                )
            )
        }
    }

    private fun behandleDokumentasjonkravHendelse(
        hendelse: Hendelse,
        block: (repository: DokumentkravRepository) -> Unit
    ) {
        block(repository)
        finalize(hendelse)
    }

    private fun finalize(hendelse: Hendelse) {
        if (!hendelse.hasMessages()) return
        if (hendelse.hasErrors()) return sikkerLogger.info("aktivitetslogg inneholder errors: ${hendelse.toLogString()}")
        sikkerLogger.info("aktivitetslogg inneholder meldinger: ${hendelse.toLogString()}")
        behovMediator.håndter(hendelse)
    }
}
