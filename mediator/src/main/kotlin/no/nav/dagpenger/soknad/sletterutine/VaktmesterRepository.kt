package no.nav.dagpenger.soknad.sletterutine

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import javax.sql.DataSource

interface VakmesterLivssyklusRepository {
    fun slettPåbegynteSøknaderEldreEnn(antallDager: Int)
}

class VaktmesterPostgresRepository(private val dataSource: DataSource) : VakmesterLivssyklusRepository {
    companion object {
        private val låseNøkkel = 123123
    }

    override fun slettPåbegynteSøknaderEldreEnn(antallDager: Int) {
        return try {
            lås()
            using(sessionOf(dataSource)) { session ->
                session.transaction { transactionalSession ->
                    val søknaderSomSkalSlettes = hentSøknaderSomSkalSlettes(transactionalSession, antallDager)
                    søknaderSomSkalSlettes.forEach { søknadUuid ->
                        slettSøknad(transactionalSession, søknadUuid)
                    }
                }
            }
        } finally {
            låsOpp()
        }
    }

    private fun hentSøknaderSomSkalSlettes(transactionalSession: TransactionalSession, antallDager: Int) =
        transactionalSession.run(
            //language=PostgreSQL
            queryOf(
                """
                SELECT s.uuid
                FROM soknad_v1 AS s
                         JOIN soknad_cache AS sc ON s.uuid = sc.uuid
                    WHERE s.tilstand = '${Påbegynt.name}'
                    AND sc.faktum_sist_endret < (now() - INTERVAL '$antallDager DAY');
                """.trimIndent()
            ).map { row ->
                row.string("uuid")
            }.asList
        )

    private fun slettSøknad(transactionalSession: TransactionalSession, søknadUuid: String) =
        transactionalSession.batchPreparedNamedStatement(
            //language=PostgreSQL
            statement = "DELETE FROM soknad_v1 WHERE uuid = :uuid",
            params = listOf(
                mapOf("uuid" to søknadUuid)
            )
        )

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
