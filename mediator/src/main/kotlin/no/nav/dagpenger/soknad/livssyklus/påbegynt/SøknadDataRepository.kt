package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Metrics.søknadDataRoundtrip
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.postgresql.util.PGobject
import java.time.Duration
import java.util.UUID
import javax.sql.DataSource

interface SøknadDataRepository {
    fun lagre(søkerOppgave: SøkerOppgave)
    fun slett(søknadUUID: UUID): Boolean
    fun hentSøkerOppgave(søknadUUID: UUID, nyereEnn: Int = 0): SøkerOppgave?
    fun besvart(søknadUUID: UUID): Int
}

class SøknadDataPostgresRepository(private val dataSource: DataSource) : SøknadDataRepository {
    override fun lagre(søkerOppgave: SøkerOppgave) {
        using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SET intervalstyle=iso_8601").asExecute)
            session.run(
                queryOf( // language=PostgreSQL
                    """INSERT INTO soknad_data(uuid, eier, soknad_data)
                            VALUES (:uuid, :eier, :data)
                            ON CONFLICT(uuid, eier)
                                DO UPDATE SET soknad_data = :data,
                                              versjon = soknad_data.versjon+1,
                                              mottatt = (NOW() AT TIME ZONE 'utc')
                                              RETURNING NOW()-sist_endret AS alder 
                    """.trimIndent(),
                    mapOf(
                        "uuid" to søkerOppgave.søknadUUID(),
                        "eier" to søkerOppgave.eier(),
                        "data" to PGobject().also {
                            it.type = "jsonb"
                            it.value = søkerOppgave.asJson()
                        }
                    )
                ).map { it.string("alder") }.asSingle
            )?.let { Duration.parse(it) }?.let {
                søknadDataRoundtrip.observe(it.toMillis().toDouble())
            }
        }
    }

    // Logger når den er sist endret, og svarer med siste versjon
    override fun besvart(søknadUUID: UUID): Int = using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf( // language=PostgreSQL
                "UPDATE soknad_data SET sist_endret=NOW() WHERE uuid = :uuid RETURNING versjon",
                mapOf("uuid" to søknadUUID)
            ).map {
                it.int("versjon")
            }.asSingle
        )
    } ?: 1

    override fun hentSøkerOppgave(søknadUUID: UUID, nyereEnn: Int): SøkerOppgave {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf( // language=PostgreSQL
                    "SELECT uuid, eier, soknad_data FROM soknad_data WHERE uuid = :uuid AND versjon > :nyereEnn",
                    mapOf(
                        "uuid" to søknadUUID,
                        "nyereEnn" to nyereEnn
                    )
                ).map { row ->
                    SøknadPostgresRepository.PersistentSøkerOppgave(objectMapper.readTree(row.binaryStream("soknad_data")))
                }.asSingle
            ) ?: throw NotFoundException("Søknad med id '$søknadUUID' ikke funnet")
        }
    }

    override fun slett(søknadUUID: UUID) =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    "DELETE FROM soknad_data WHERE uuid = ?",
                    søknadUUID
                ).asExecute
            )
        }
}
