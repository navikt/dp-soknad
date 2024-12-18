package no.nav.dagpenger.soknad.innsending.tjenester

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingPåminnelseHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import java.util.UUID
import javax.sql.DataSource
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.minutes

internal class PåminnelseJobb(private val innsendingMediator: InnsendingMediator, private val datasource: DataSource) {
    private val logger = KotlinLogging.logger {}

    fun påminn() {
        fixedRateTimer(
            name = "Innsending påminnelse jobb",
            daemon = true,
            initialDelay = 1.minutes.inWholeMilliseconds,
            period = 2.minutes.inWholeMilliseconds,
            action = {
                try {
                    logger.info { "Henter innsendinger som trenger påminnelse" }
                    val påminnelseHendelser = hentInnsendingerSomTrengerPåminnelse().mapNotNull {
                        innsendingMediator.hent(it)?.let { innsending ->
                            InnsendingPåminnelseHendelse(innsending.innsendingId, innsending.hentEier())
                        }
                    }

                    logger.info { "Håndterer innsending påminnelse for ${påminnelseHendelser.size} innsendinger" }

                    påminnelseHendelser.forEach {
                        logger.info { "Håndterer innsending påminnelse for innsending med id ${it.innsendingId}" }
                        innsendingMediator.behandle(it)
                    }
                } catch (e: Exception) {
                    logger.error { "Påminnelse om innsendinger jobb feilet: $e" }
                }
            },
        )
    }

    private fun hentInnsendingerSomTrengerPåminnelse(): List<UUID> {
        return using(sessionOf(datasource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    """
                    SELECT innsending_uuid
                    FROM innsending_v1
                      WHERE tilstand != 'Journalført'
                      AND innsendt > '2023.01.01'
                      AND innsending_uuid = 'ab54d26c-fbc6-4039-8dcc-c7c783e98f8a'
                        AND journalpost_id IS NULL;
                    """.trimIndent(),
                ).map {
                    it.uuid("innsending_uuid")
                }.asList,
            )
        }
    }
}
