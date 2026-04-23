package no.nav.dagpenger.soknad.innsending.tjenester

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.hendelse.innsending.InnsendingPåminnelseHendelse
import no.nav.dagpenger.soknad.innsending.InnsendingMediator
import no.nav.dagpenger.soknad.sletterutine.medLås
import java.util.UUID
import javax.sql.DataSource

internal class PåminnelseJobb(
    private val innsendingMediator: InnsendingMediator,
    private val datasource: DataSource,
) {
    private val logger = KotlinLogging.logger {}
    private val låseNøkkel = 45678

    fun påminn() {
        try {
            logger.info { "Henter innsendinger som trenger påminnelse" }
            val påminnelseHendelser =
                hentInnsendingerSomTrengerPåminnelse().mapNotNull {
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
    }

    private fun hentInnsendingerSomTrengerPåminnelse(): List<UUID> =
        using(sessionOf(datasource)) { session ->
            session.medLås(låseNøkkel) {
                session.transaction { tx ->
                    tx.run(
                        queryOf(
                            //language=PostgreSQL
                            """
                            SELECT innsending_uuid
                            FROM innsending_v1
                            WHERE innsendt > '2023.01.01'
                              AND innsendt < '2024-12-18'
                              AND tilstand = 'AvventerMidlertidligJournalføring';
                            """.trimIndent(),
                        ).map {
                            it.uuid("innsending_uuid")
                        }.asList,
                    )
                }
            } ?: emptyList()
        }
}
