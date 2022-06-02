package no.nav.dagpenger.soknad.sletterutine

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type
import java.time.LocalDateTime
import javax.sql.DataSource

interface VakmesterLivsyklusRepository {
    fun slettPåbegynteSøknaderEldreEnn(tidspunkt: LocalDateTime): Int
}

class VaktmesterPostgresRepository(private val dataSource: DataSource) : VakmesterLivsyklusRepository {
    companion object {
        private val låseNøkkel = 123123
    }

    override fun slettPåbegynteSøknaderEldreEnn(tidspunkt: LocalDateTime): Int {
        return try {
            lås()
            using(sessionOf(dataSource)) { session ->
                session.run(
                    //language=PostgreSQL
                    queryOf(
                        "DELETE FROM soknad_v1 WHERE opprettet<:tidspunkt AND tilstand=:tilstand",
                        mapOf("tidspunkt" to tidspunkt, "tilstand" to Type.Påbegynt.name)
                    ).asUpdate
                )
            }
        } finally {
            låsOpp()
        }
    }

    private fun lås(): Boolean {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf( //language=PostgreSQL
                    "SELECT pg_try_advisory_lock(:key)", mapOf("key" to låseNøkkel)
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
                    "SELECT pg_advisory_unlock(:key)", mapOf("key" to låseNøkkel)
                ).map { res ->
                    res.boolean("pg_advisory_unlock")
                }.asSingle
            ) ?: false
        }
    }
}
