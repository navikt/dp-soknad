package no.nav.dagpenger.soknad.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import org.postgresql.util.PGobject
import java.util.UUID
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

internal class InnsendtSoknadRepository(private val ds: DataSource) {
    fun lagre(søknadUuid: UUID, soknad: String) {
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    statement = "INSERT INTO innsendt_soknad_v1(uuid,soknad_med_tekst) VALUES(:uuid,:json)",
                    paramMap = mapOf(
                        "uuid" to søknadUuid.toString(),
                        "json" to PGobject().also {
                            it.type = "jsonb"
                            it.value = soknad
                        }
                    )
                ).asUpdate
            )
        }
    }

    fun hent(søknadId: UUID): String {
        return using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = "SELECT   soknad_med_tekst FROM innsendt_soknad_v1 WHERE uuid = :uuid",
                    paramMap = mapOf(
                        "uuid" to søknadId.toString()
                    )
                ).map { row -> row.string("soknad_med_tekst") }.asSingle
            ) ?: throw SoknadNotFoundException(søknadId.toString()).also {
                logger.error { "Fant ikke søknad med id: $søknadId" }
            }
        }
    }
}

internal class SoknadNotFoundException(id: String) : RuntimeException("Fant ikke innsendt søknad: $id")
