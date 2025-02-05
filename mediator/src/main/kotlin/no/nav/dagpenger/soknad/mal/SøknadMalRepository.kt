package no.nav.dagpenger.soknad.mal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import no.nav.dagpenger.soknad.Prosessnavn
import no.nav.dagpenger.soknad.Prosessversjon
import no.nav.dagpenger.soknad.mal.SøknadMalRepository.SøknadMalObserver
import org.postgresql.util.PGobject
import javax.sql.DataSource

interface SøknadMalRepository {
    fun lagre(søknadMal: SøknadMal): Int

    fun hentNyesteMal(prosessnavn: Prosessnavn): SøknadMal

    fun addObserver(søknadMalObserver: SøknadMalObserver): Boolean

    fun prosessnavn(prosessnavn: String): Prosessnavn

    fun prosessversjon(
        prosessnavn: String,
        versjon: Int,
    ): Prosessversjon

    fun interface SøknadMalObserver {
        fun nyMal(søknadMal: SøknadMal)
    }
}

class SøknadMalPostgresRepository(private val dataSource: DataSource) : SøknadMalRepository {
    private val observers = mutableListOf<SøknadMalObserver>()

    override fun lagre(søknadMal: SøknadMal) =
        using(sessionOf(dataSource)) { session: Session ->
            session.transaction { tx ->
                tx.run(
                    queryOf( //language=PostgreSQL
                        "INSERT INTO soknadmal (prosessnavn, prosessversjon, mal) VALUES (:prosessnavn, :prosessversjon, :mal) ON CONFLICT DO NOTHING ",
                        mapOf(
                            "prosessnavn" to søknadMal.prosessversjon.prosessnavn.id,
                            "prosessversjon" to søknadMal.prosessversjon.versjon,
                            "mal" to
                                PGobject().apply {
                                    this.type = "jsonb"
                                    this.value = søknadMal.mal.toString()
                                },
                        ),
                    ).asUpdate,
                )
            }
        }.also { nyMal ->
            if (nyMal == 1) {
                observers.forEach { it.nyMal(søknadMal) }.also {
                    logger.info { "Varsler ${observers.size} om ny mal" }
                }
            }
        }

    override fun hentNyesteMal(prosessnavn: Prosessnavn): SøknadMal =
        using(sessionOf(dataSource)) { session: Session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    "SELECT * FROM soknadmal WHERE prosessnavn = :prosessnavn ORDER BY prosessversjon DESC LIMIT 1",
                    mapOf("prosessnavn" to prosessnavn.id),
                ).map { row ->
                    SøknadMal(
                        Prosessversjon(
                            Prosessnavn(row.string("prosessnavn")),
                            row.int("prosessversjon"),
                        ),
                        objectMapper.readTree(row.binaryStream("mal")),
                    )
                }.asSingle,
            )
        } ?: throw IngenMalFunnetException()

    override fun prosessnavn(prosessnavn: String) =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf( // language=PostgreSQL
                    "SELECT prosessnavn FROM soknadmal WHERE prosessnavn = ?",
                    prosessnavn,
                ).map {
                    Prosessnavn(it.string("prosessnavn"))
                }.asSingle,
            )
        } ?: throw IllegalArgumentException("Kjenner ikke til prosessnavn=$prosessnavn")

    override fun prosessversjon(
        prosessnavn: String,
        versjon: Int,
    ): Prosessversjon =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf( // language=PostgreSQL
                    "SELECT prosessnavn, prosessversjon FROM soknadmal WHERE prosessnavn = ? AND prosessversjon = ?",
                    prosessnavn,
                    versjon,
                ).map {
                    Prosessversjon(
                        Prosessnavn(it.string("prosessnavn")),
                        it.int("prosessversjon"),
                    )
                }.asSingle,
            )
        } ?: throw IllegalArgumentException("Kjenner ikke til prosessnavn=$prosessnavn, prosessversjon=$versjon")

    private companion object {
        private val objectMapper = jacksonObjectMapper()
        private val logger = KotlinLogging.logger {}
    }

    override fun addObserver(søknadMalObserver: SøknadMalObserver) = observers.add(søknadMalObserver)
}

data class SøknadMal(val prosessversjon: Prosessversjon, val mal: JsonNode)

class IngenMalFunnetException(override val message: String? = "Fant ingen søknadmal") : RuntimeException(message)
