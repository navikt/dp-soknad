package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.db.SøknadPostgresRepository
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.postgresql.util.PGobject
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

interface SøknadCacheRepository {
    fun lagre(søkerOppgave: SøkerOppgave)
    fun slett(søknadUUID: UUID): Boolean
    fun hentSøkerOppgave(søknadUUID: UUID, sistLagretEtter: LocalDateTime? = null): SøkerOppgave?
    fun besvart(søknadUUID: UUID, besvart: LocalDateTime): Int
}

class SøknadCachePostgresRepository(private val dataSource: DataSource) : SøknadCacheRepository {
    override fun lagre(søkerOppgave: SøkerOppgave) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        // language=PostgreSQL
                        """INSERT INTO soknad_cache(uuid, eier, soknad_data, sist_endret)
                            VALUES (:uuid, :eier, :data, :sist_endret)
                            ON CONFLICT(uuid, eier)
                                DO UPDATE SET soknad_data = :data,
                                              sist_endret = :sist_endret,
                                              mottatt = (NOW() AT TIME ZONE 'utc')
                        """.trimIndent(),
                        mapOf(
                            "uuid" to søkerOppgave.søknadUUID(),
                            "eier" to søkerOppgave.eier(),
                            "sist_endret" to LocalDateTime.now(),
                            "data" to PGobject().also {
                                it.type = "jsonb"
                                it.value = søkerOppgave.asJson()
                            }
                        )
                    ).asUpdate
                )
            }
        }
    }

    override fun besvart(søknadUUID: UUID, besvart: LocalDateTime): Int {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    // language=PostgreSQL
                    "UPDATE soknad_cache SET sist_endret=:sistEndret WHERE uuid = :uuid",
                    mapOf(
                        "uuid" to søknadUUID,
                        "sistEndret" to besvart
                    )
                ).asUpdate
            )
        }
    }

    override fun hentSøkerOppgave(søknadUUID: UUID, sistLagretEtter: LocalDateTime?): SøkerOppgave {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    // language=PostgreSQL
                    "SELECT uuid, eier, soknad_data FROM soknad_cache WHERE uuid = :uuid AND (:sistLagret::timestamp IS NULL OR sist_endret > :sistLagret)",
                    mapOf(
                        "uuid" to søknadUUID,
                        "sistLagret" to sistLagretEtter
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
                    "DELETE FROM soknad_cache WHERE uuid = ?",
                    søknadUUID
                ).asExecute
            )
        }
}
