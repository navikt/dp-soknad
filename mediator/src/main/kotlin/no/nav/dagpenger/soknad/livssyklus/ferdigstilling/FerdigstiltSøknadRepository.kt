package no.nav.dagpenger.soknad.livssyklus.ferdigstilling

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import org.postgresql.util.PGobject
import org.postgresql.util.PSQLException
import java.util.UUID
import javax.sql.DataSource

interface FerdigstiltSøknadRepository {
    fun lagreSøknadsTekst(søknadUuid: UUID, søknadsTekst: String)
    fun hentTekst(søknadId: UUID): String
    fun hentFakta(søknadId: UUID): String
}

private val logger = KotlinLogging.logger {}

internal class FerdigstiltSøknadPostgresRepository(private val dataSource: DataSource) : FerdigstiltSøknadRepository {
    override fun lagreSøknadsTekst(søknadUuid: UUID, søknadsTekst: String) {
        try {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        //language=PostgreSQL
                        statement = """INSERT INTO soknad_tekst_v1(uuid,tekst) VALUES(:uuid,:tekst)
ON CONFLICT (uuid) DO NOTHING """,
                        paramMap = mapOf(
                            "uuid" to søknadUuid,
                            "tekst" to PGobject().also {
                                it.type = "jsonb"
                                it.value = søknadsTekst
                            },
                        ),
                    ).asUpdate,
                )
            }
        } catch (error: PSQLException) {
            logger.error(error) { "Feil i lagring av søknad tekst for søknad: $søknadUuid" }
            throw error
        }
    }

    override fun hentTekst(søknadId: UUID): String {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = "SELECT tekst FROM soknad_tekst_v1 WHERE uuid = :uuid",
                    paramMap = mapOf(
                        "uuid" to søknadId,
                    ),
                ).map { row -> row.string("tekst") }.asSingle,
            ) ?: throw NotFoundException().also {
                logger.error { "Fant ikke søknad tekst med id: $søknadId" }
            }
        }
    }

    override fun hentFakta(søknadId: UUID): String {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = "SELECT soknad_data FROM soknad_data WHERE uuid = :uuid",
                    paramMap = mapOf(
                        "uuid" to søknadId,
                    ),
                ).map { row -> row.string("soknad_data") }.asSingle,
            ) ?: throw NotFoundException().also {
                logger.error { "Fant ikke søknad data med id: $søknadId" }
            }
        }
    }
}
