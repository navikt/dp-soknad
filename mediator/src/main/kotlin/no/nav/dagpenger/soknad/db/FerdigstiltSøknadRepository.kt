package no.nav.dagpenger.soknad.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import org.postgresql.util.PGobject
import org.postgresql.util.PSQLException
import java.util.UUID
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

internal class FerdigstiltSøknadRepository(private val ds: DataSource) {
    fun lagreSøknadsTekst(søknadUuid: UUID, søknadsTekst: String) {
        try {

            using(sessionOf(ds)) { session ->
                session.run(
                    queryOf(
                        statement = "INSERT INTO soknad_tekst_v1(uuid,tekst) VALUES(:uuid,:tekst)",
                        paramMap = mapOf(
                            "uuid" to søknadUuid.toString(),
                            "tekst" to PGobject().also {
                                it.type = "jsonb"
                                it.value = søknadsTekst
                            }
                        )
                    ).asUpdate
                )
            }
        } catch (error: PSQLException) {
            if (error.sqlState == "23505") {
                logger.error { "Forsøk på å legge inn duplikat i innsendt søknad: $søknadUuid" }
                throw IllegalArgumentException(error)
            } else {
                logger.error(error) { "Ukjent feil" }
                throw error
            }
        }
    }

    fun hentTekst(søknadId: UUID): String {
        return using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = "SELECT tekst FROM  soknad_tekst_v1 WHERE uuid = :uuid",
                    paramMap = mapOf(
                        "uuid" to søknadId.toString()
                    )
                ).map { row -> row.string("tekst") }.asSingle
            ) ?: throw SoknadNotFoundException(søknadId.toString()).also {
                logger.error { "Fant ikke søknad tekst med id: $søknadId" }
            }
        }
    }
}

internal class SoknadNotFoundException(id: String) : RuntimeException("Fant ikke innsendt søknad: $id")
