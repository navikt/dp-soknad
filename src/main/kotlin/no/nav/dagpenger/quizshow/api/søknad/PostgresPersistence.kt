package no.nav.dagpenger.quizshow.api.søknad

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.features.NotFoundException
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.quizshow.api.Persistence
import no.nav.dagpenger.quizshow.api.Søknad
import no.nav.dagpenger.quizshow.api.serder.objectMapper
import org.postgresql.util.PGobject
import java.util.UUID
import javax.sql.DataSource

class PostgresPersistence(private val dataSource: DataSource) : Persistence {

    override fun lagre(søknad: Søknad) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->

                tx.run(
                    // language=PostgreSQL
                    queryOf(
                        """INSERT INTO soknad(uuid, eier, soknad_data)
                                    VALUES (:uuid, :eier, :data)
                                    ON CONFLICT(uuid, eier) 
                                    DO UPDATE SET soknad_data = :data,
                                    opprettet = (NOW() AT TIME ZONE 'utc')
                                    """,
                        mapOf(
                            "uuid" to søknad.søknadUUID(),
                            "eier" to søknad.eier(),
                            "data" to PGobject().also {
                                it.type = "jsonb"
                                it.value = søknad.asJson().toString()
                            }
                        )
                    ).asUpdate,
                )
            }
        }
    }

    override fun hent(søknadUUID: UUID): Søknad {
        // language=PostgreSQL
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT uuid, eier, soknad_data FROM SOKNAD WHERE uuid = :uuid",
                    mapOf("uuid" to søknadUUID.toString())
                ).map { row ->
                    PersistentSøknad(objectMapper.readTree(row.binaryStream("soknad_data")))
                }.asSingle
            ) ?: throw NotFoundException("Søknad med id '$søknadUUID' ikke funnet")
        }
    }

    internal class PersistentSøknad(private val søknad: JsonNode) : Søknad {
        override fun søknadUUID(): UUID = UUID.fromString(søknad[Søknad.Keys.SØKNAD_UUID].asText())
        override fun eier(): String = søknad[Søknad.Keys.FØDSELSNUMMER].asText()
        override fun fakta(): JsonNode = søknad[Søknad.Keys.FAKTA]
        override fun asJson(): String = søknad.toString()
    }
}
