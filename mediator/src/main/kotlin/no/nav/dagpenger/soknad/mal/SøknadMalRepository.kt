package no.nav.dagpenger.soknad.mal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.mal.SøknadMalRepository.SøknadMalObserver
import org.postgresql.util.PGobject
import javax.sql.DataSource

interface SøknadMalRepository {
    fun lagre(søknadMal: SøknadMal): Int
    fun hentNyesteMal(prosessnavn: String): SøknadMal
    fun addObserver(søknadMalObserver: SøknadMalObserver): Boolean
    fun interface SøknadMalObserver {
        fun nyMal(søknadMal: SøknadMal)
    }
}

class SøknadMalPostgresRepository(private val dataSource: DataSource) : SøknadMalRepository {

    private val observers = mutableListOf<SøknadMalObserver>()

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
        }.also {
            observers.forEach { it.nyMal(søknadMal) }
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

    override fun addObserver(søknadMalObserver: SøknadMalObserver) = observers.add(søknadMalObserver)
}

data class SøknadMal(val prosessnavn: String, val prosessversjon: Int, val mal: JsonNode)

class IngenMalFunnetException(override val message: String? = "Fant ingen søknadmal") : RuntimeException(message)
