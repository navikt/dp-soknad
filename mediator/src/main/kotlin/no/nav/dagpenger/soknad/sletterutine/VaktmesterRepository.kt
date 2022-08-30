package no.nav.dagpenger.soknad.sletterutine

import kotliquery.Row
import kotliquery.Session
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
    fun slettPåbegynteSøknaderEldreEnn(antallDager: Int): List<Int>?
}

private val logger = KotlinLogging.logger {}

internal class VaktmesterPostgresRepository(
    private val dataSource: DataSource,
    private val søknadMediator: SøknadMediator
) : VakmesterLivssyklusRepository {
    override fun slettPåbegynteSøknaderEldreEnn(antallDager: Int) =
        using(sessionOf(dataSource)) { session ->
            session.medLås(låseNøkkel) {
                session.transaction { transactionalSession ->
                    val søknaderSomSkalSlettes = hentPåbegynteSøknaderUendretSiden(antallDager, transactionalSession)
                    logger.info("Antall søknader som skal slettes: ${søknaderSomSkalSlettes.size}")

                    søknaderSomSkalSlettes.forEach { søknad ->
                        emitSlettSøknadEvent(søknad)
                    }

                    slettSøknader(søknaderSomSkalSlettes, transactionalSession)
                }
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

    private fun slettSøknader(
        søknadUuider: List<SøknadTilSletting>,
        transactionalSession: TransactionalSession
    ): List<Int> {
        val iderTilSletting = søknadUuider.map { listOf(it.søknadUuid.toString()) }
        return transactionalSession.batchPreparedStatement(
            //language=PostgreSQL
            "DELETE FROM soknad_v1 WHERE uuid =?",
            iderTilSletting
        )
    }

    companion object {
        val låseNøkkel = 123123
    }
}

fun Session.lås(nøkkel: Int) = run(
    queryOf( //language=PostgreSQL
        "SELECT PG_TRY_ADVISORY_LOCK(:key)",
        mapOf("key" to nøkkel)
    ).map { res ->
        res.boolean("pg_try_advisory_lock")
    }.asSingle
) ?: false

fun Session.låsOpp(nøkkel: Int) = run(
    queryOf( //language=PostgreSQL
        "SELECT PG_ADVISORY_UNLOCK(:key)",
        mapOf("key" to nøkkel)
    ).map { res ->
        res.boolean("pg_advisory_unlock")
    }.asSingle
) ?: false

fun <T> Session.medLås(nøkkel: Int, block: () -> T): T? {
    if (!lås(nøkkel)) {
        logger.warn { "Fikk ikke lås for $nøkkel" }
        return null
    }
    return try {
        logger.info { "Fikk lås for $nøkkel" }
        block()
    } finally {
        logger.info { "Låser opp $nøkkel" }
        låsOpp(nøkkel)
    }
}
