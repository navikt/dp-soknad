package no.nav.dagpenger.soknad.db

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.mottak.SøkerOppgave
import no.nav.dagpenger.soknad.serder.objectMapper
import org.postgresql.util.PGobject
import java.util.UUID
import javax.sql.DataSource

interface SøknadCacheRepository {
    fun lagre(søkerOppgave: SøkerOppgave)
    fun slett(søknadUUID: UUID, eier: String): Boolean
    fun hent(søknadUUID: UUID): SøkerOppgave?
}

class SøknadCachePostgresRepository(private val dataSource: DataSource) : SøknadCacheRepository {

    override fun lagre(søkerOppgave: SøkerOppgave) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    // language=PostgreSQL
                    queryOf(
                        """INSERT INTO soknad_cache(uuid, eier, soknad_data)
                                    VALUES (:uuid, :eier, :data)
                                    ON CONFLICT(uuid, eier) 
                                    DO UPDATE SET soknad_data = :data,
                                    opprettet = (NOW() AT TIME ZONE 'utc')""",
                        mapOf(
                            "uuid" to søkerOppgave.søknadUUID(),
                            "eier" to søkerOppgave.eier(),
                            "data" to PGobject().also {
                                it.type = "jsonb"
                                it.value = søkerOppgave.asJson().toString()
                            }
                        )
                    ).asUpdate,
                )
            }
        }
    }

    override fun hent(søknadUUID: UUID): SøkerOppgave {
        // language=PostgreSQL
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT uuid, eier, soknad_data FROM soknad_cache WHERE uuid = :uuid",
                    mapOf("uuid" to søknadUUID.toString())
                ).map { row ->
                    LivsyklusPostgresRepository.PersistentSøkerOppgave(objectMapper.readTree(row.binaryStream("soknad_data")))
                }.asSingle
            ) ?: throw NotFoundException("Søknad med id '$søknadUUID' ikke funnet")
        }
    }

    override fun slett(søknadUUID: UUID, eier: String): Boolean =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    "DELETE FROM soknad_cache WHERE uuid = ? AND eier = ?", søknadUUID.toString(), eier
                ).asExecute
            )
        }
}
