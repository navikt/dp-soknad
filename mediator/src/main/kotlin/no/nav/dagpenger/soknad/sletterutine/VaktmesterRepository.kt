package no.nav.dagpenger.soknad.sletterutine

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.SøknadMediator
import no.nav.dagpenger.soknad.hendelse.SlettSøknadHendelse
import java.util.UUID
import javax.sql.DataSource

internal interface VakmesterLivssyklusRepository {
    fun slettPåbegynteSøknaderEldreEnn(antallDager: Int)
}

private val logger = KotlinLogging.logger {}

internal class VaktmesterPostgresRepository(
    private val dataSource: DataSource,
    private val søknadMediator: SøknadMediator
) : VakmesterLivssyklusRepository {

    override fun slettPåbegynteSøknaderEldreEnn(antallDager: Int) {
        return try {
            lås()
            using(sessionOf(dataSource)) { session ->
                session.transaction { transactionalSession ->
                    val søknaderSomSkalSlettes = hentPåbegynteSøknaderUendretSiden(antallDager, transactionalSession)
                    logger.info("Antall søknader som skal slettes: ${søknaderSomSkalSlettes.size}")

                    søknaderSomSkalSlettes.forEach { søknad ->
                        emitSlettSøknadEvent(søknad)
                    }

                    slettSøknader(søknaderSomSkalSlettes, transactionalSession)
                }
            }
        } finally {
            låsOpp()
        }
    }

    private fun emitSlettSøknadEvent(søknad: SøknadTilSletting) =
        søknadMediator.behandle(SlettSøknadHendelse(søknad.søknadUuid, søknad.eier))

    private fun hentPåbegynteSøknaderUendretSiden(antallDager: Int, transactionalSession: TransactionalSession) =
        transactionalSession.run(
            //language=PostgreSQL
            queryOf(
                """
                SELECT uuid, person_ident
                FROM soknad_v1
                    WHERE tilstand = '${Påbegynt.name}'
                    AND sist_endret_av_bruker < (now() - INTERVAL '$antallDager DAY');
                """.trimIndent()
            ).map(søknadTilSletting).asList
        )

    private data class SøknadTilSletting(val søknadUuid: UUID, val eier: String)

    private val søknadTilSletting: (Row) -> SøknadTilSletting = { row ->
        SøknadTilSletting(
            søknadUuid = UUID.fromString(row.string("uuid")),
            eier = row.string("person_ident")
        )
    }

    private fun slettSøknader(søknadUuider: List<SøknadTilSletting>, transactionalSession: TransactionalSession) {
        val iderTilSletting = søknadUuider.map { listOf(it.søknadUuid.toString()) }
        transactionalSession.batchPreparedStatement(
            //language=PostgreSQL
            "DELETE FROM soknad_v1 WHERE uuid =?", iderTilSletting
        )
    }

    companion object {
        private val låseNøkkel = 123123
    }

    private fun lås(): Boolean {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf( //language=PostgreSQL
                    "SELECT PG_TRY_ADVISORY_LOCK(:key)", mapOf("key" to låseNøkkel)
                ).map { res ->
                    res.boolean("pg_try_advisory_lock")
                }.asSingle
            ) ?: false
        }
    }

    private fun låsOpp(): Boolean {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf( //language=PostgreSQL
                    "SELECT PG_ADVISORY_UNLOCK(:key)", mapOf("key" to låseNøkkel)
                ).map { res ->
                    res.boolean("pg_advisory_unlock")
                }.asSingle
            ) ?: false
        }
    }
}
