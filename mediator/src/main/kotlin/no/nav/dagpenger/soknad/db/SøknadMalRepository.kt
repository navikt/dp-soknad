package no.nav.dagpenger.soknad.db

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.postgresql.util.PGobject
import javax.sql.DataSource

interface SøknadMalRepository {
    fun lagre(søknadMal: SøknadMal): Int
    fun hentNyesteMal(prosessnavn: String): SøknadMal
}

class SøknadMalPostgresRepository(private val dataSource: DataSource) : SøknadMalRepository {

    override fun lagre(søknadMal: SøknadMal): Int {
        return using(sessionOf(dataSource)) { session: Session ->
            session.transaction { transactionalSession ->
                transactionalSession.run(
                    queryOf(
                        //language=PostgreSQL
                        "INSERT INTO soknadmal (prosessnavn, prosessversjon, mal) VALUES (:prosessnavn, :prosessversjon, :mal) ON CONFLICT DO NOTHING ",
                        mapOf(
                            "prosessnavn" to søknadMal.prosessnavn,
                            "prosessversjon" to søknadMal.prosessversjon,
                            "mal" to PGobject().apply {
                                this.type = "jsonb"
                                this.value = søknadMal.mal.toString()
                            }
                        )
                    ).asUpdate
                )
            }
        }
    }

    override fun hentNyesteMal(prosessnavn: String): SøknadMal = using(sessionOf(dataSource)) { session: Session ->
        session.run(
            queryOf(
                //language=PostgreSQL
                "SELECT * FROM soknadmal where prosessnavn = :prosessnavn ORDER BY prosessversjon DESC LIMIT 1",
                mapOf("prosessnavn" to prosessnavn)
            ).map { row ->
                SøknadMal(
                    row.string("prosessnavn"),
                    row.int("prosessversjon"),
                    objectMapper.readTree(row.binaryStream("mal"))
                )
            }.asSingle
        )
    } ?: throw IngenMalFunnetException()

    companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}

data class SøknadMal(val prosessnavn: String, val prosessversjon: Int, val mal: JsonNode)

class IngenMalFunnetException(override val message: String? = "Fant ingen søknadmal") : RuntimeException(message)
