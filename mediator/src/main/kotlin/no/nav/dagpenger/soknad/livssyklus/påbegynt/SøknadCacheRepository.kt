package no.nav.dagpenger.soknad.livssyklus.påbegynt

import io.ktor.server.plugins.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.livssyklus.LivssyklusPostgresRepository
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import org.postgresql.util.PGobject
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

interface SøknadCacheRepository {
    fun lagre(søkerOppgave: SøkerOppgave)
    fun slett(søknadUUID: UUID, eier: String): Boolean
    fun hent(søknadUUID: UUID): SøkerOppgave?
    fun settFaktumSistEndret(søknadUUID: UUID)
    fun hentFaktumSistEndret(søknadUUID: UUID): ZonedDateTime
}

class SøknadCachePostgresRepository(private val dataSource: DataSource) : SøknadCacheRepository {
    override fun lagre(søkerOppgave: SøkerOppgave) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf( // TODO: Se på opprettet, trenger ikke denne?
                        // language=PostgreSQL
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
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    // language=PostgreSQL
                    "SELECT uuid, eier, soknad_data, faktum_sist_endret FROM soknad_cache WHERE uuid = :uuid",
                    mapOf("uuid" to søknadUUID.toString())
                ).map { row ->
                    LivssyklusPostgresRepository.PersistentSøkerOppgave(objectMapper.readTree(row.binaryStream("soknad_data")))
                }.asSingle
            ) ?: throw NotFoundException("Søknad med id '$søknadUUID' ikke funnet")
        }
    }

    override fun settFaktumSistEndret(søknadUUID: UUID) {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    "UPDATE soknad_cache SET faktum_sist_endret = (NOW() AT TIME ZONE 'utc') WHERE uuid = ? RETURNING uuid",
                    søknadUUID.toString()
                ).map { row ->
                    row.string("uuid")
                }.asSingle
            ) ?: throw NotFoundException("Søknad med id '$søknadUUID' ikke funnet")
        }
    }

    override fun hentFaktumSistEndret(søknadUUID: UUID): ZonedDateTime =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    "SELECT faktum_sist_endret FROM soknad_cache WHERE uuid = ?",
                    søknadUUID.toString()
                ).map { row ->
                    row.zonedDateTime("faktum_sist_endret")
                }.asSingle
            )
        } ?: throw NotFoundException("Søknad med id '$søknadUUID' ikke funnet")

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
