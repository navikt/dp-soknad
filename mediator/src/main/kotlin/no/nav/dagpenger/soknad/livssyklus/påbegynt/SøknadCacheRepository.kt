package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.postgresql.util.PGobject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

interface SøknadCacheRepository {
    fun lagre(søkerOppgave: SøkerOppgave)
    fun slett(søknadUUID: UUID, eier: String): Boolean
    fun hent(søknadUUID: UUID, sistLagretEtter: LocalDateTime? = null): SøkerOppgave?
}

class SøknadCachePostgresRepository(private val dataSource: DataSource) : SøknadCacheRepository {
    override fun lagre(søkerOppgave: SøkerOppgave) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        // language=PostgreSQL
                        """INSERT INTO soknad_cache(uuid, eier, soknad_data, opprettet)
                            VALUES (:uuid, :eier, :data, :opprettet)
                            ON CONFLICT(uuid, eier)
                                DO UPDATE SET soknad_data = :data,
                                              opprettet = :opprettet,
                                              mottatt = (NOW() AT TIME ZONE 'utc')
                        """.trimIndent(),
                        mapOf(
                            "uuid" to søkerOppgave.søknadUUID(),
                            "eier" to søkerOppgave.eier(),
                            "opprettet" to søkerOppgave.opprettet(),
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

    override fun hent(søknadUUID: UUID, sistLagretEtter: LocalDateTime?): SøkerOppgave {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    // language=PostgreSQL
                    "SELECT uuid, eier, soknad_data FROM soknad_cache WHERE uuid = :uuid AND (:sistLagret::timestamptz IS NULL OR opprettet > :sistLagret)",
                    mapOf(
                        "uuid" to søknadUUID.toString(),
                        "sistLagret" to sistLagretEtter // ?.toLocalDateTimeWithTimezone()
                    )
                ).map { row ->
                    LivssyklusPostgresRepository.PersistentSøkerOppgave(objectMapper.readTree(row.binaryStream("soknad_data")))
                }.asSingle
            ) ?: throw NotFoundException("Søknad med id '$søknadUUID' ikke funnet")
        }
    }

    override fun slett(søknadUUID: UUID, eier: String): Boolean =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    "DELETE FROM soknad_cache WHERE uuid = ? AND eier = ?",
                    søknadUUID.toString(),
                    eier
                ).asExecute
            )
        }
}

private fun LocalDateTime?.toLocalDateTimeWithTimezone() =
    this?.atZone(ZoneId.systemDefault())?.withZoneSameInstant(ZoneOffset.UTC)
