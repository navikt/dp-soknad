package no.nav.dagpenger.soknad.db

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.ktor.server.plugins.NotFoundException
import kotliquery.Session
import kotliquery.action.UpdateQueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.serder.AktivitetsloggMapper.Companion.aktivitetslogg
import no.nav.dagpenger.soknad.serder.PersonData
import no.nav.dagpenger.soknad.serder.PersonData.SøknadData
import no.nav.dagpenger.soknad.serder.objectMapper
import no.nav.dagpenger.soknad.søknad.SøkerOppgave
import no.nav.dagpenger.soknad.toMap
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class LivsyklusPostgresRepository(private val dataSource: DataSource) : LivsyklusRepository {

    override fun hent(ident: String): Person? {
        return using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        //language=PostgreSQL
                        """
                                SELECT p.id AS person_id, p.ident AS person_ident, a.data AS aktivitetslogg 
                                FROM person_v1 AS p, aktivitetslogg_v1 AS a 
                                WHERE p.id=a.id AND p.ident=:ident
                        """.trimIndent(),
                        paramMap = mapOf("ident" to ident)
                    ).map { r ->
                        r.stringOrNull("person_ident")?.let { ident ->
                            PersonData(
                                ident = r.string("person_ident"),
                                aktivitetsLogg = r.binaryStream("aktivitetslogg").aktivitetslogg(),
                                søknader = tx.hentSøknadsData(ident)
                            )
                        }
                    }.asSingle
                )?.createPerson()
            }
        }
    }

    override fun lagre(person: Person) {
        val visitor = PersonPersistenceVisitor(person)
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                val internId: Long = tx.run(
                    queryOf(
                        //language=PostgreSQL
                        "SELECT id FROM person_v1 WHERE ident=:ident",
                        mapOf("ident" to person.ident())
                    ).map { row -> row.longOrNull("id") }.asSingle
                ) ?: tx.run(
                    queryOf(
                        //language=PostgreSQL
                        "INSERT INTO person_v1(ident) VALUES(:ident) ON CONFLICT DO NOTHING RETURNING id",
                        paramMap = mapOf("ident" to visitor.ident)
                    ).map { row ->
                        row.long("id")
                    }.asSingle
                )!!

                tx.run(
                    queryOf(
                        //language=PostgreSQL
                        statement = "INSERT INTO aktivitetslogg_v1 (id, data ) VALUES (:id, :data ) ON CONFLICT(id) DO UPDATE SET data =:data",
                        paramMap = mapOf(
                            "id" to internId,
                            "data" to PGobject().apply {
                                type = "jsonb"
                                value = objectMapper.writeValueAsString(visitor.aktivitetslogg.toMap())
                            }
                        )
                    ).asUpdate
                )
                visitor.søknader.forEach {
                    tx.run(it.insertQuery(visitor.ident))
                    it.insertDokumentQuery(tx)
                }
            }
        }
    }

    override fun hentPåbegynte(personIdent: String): List<PåbegyntSøknad> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                (
                    queryOf(
                        "SELECT uuid, opprettet FROM soknad_v1 WHERE person_ident=:ident AND tilstand=:paabegyntTilstand",
                        mapOf("ident" to personIdent, "paabegyntTilstand" to Påbegynt.name)
                    ).map { r ->
                        PåbegyntSøknad(UUID.fromString(r.string("uuid")), r.localDate("opprettet"))
                    }
                    ).asList
            )
        }
    }

    private fun Session.hentDokumentData(søknadId: UUID): List<SøknadData.DokumentData> {
        return this.run(
            queryOf(
                //language=PostgreSQL
                statement = """
                SELECT dokument_lokasjon FROM dokument_v1 
                WHERE soknad_uuid = :soknadId
                """.trimIndent(),
                paramMap = mapOf(
                    "soknadId" to søknadId.toString()
                )
            ).map { row ->
                SøknadData.DokumentData(urn = row.string("dokument_lokasjon"))
            }.asList
        )
    }

    private fun Session.hentSøknadsData(ident: String): List<SøknadData> {
        return this.run(
            queryOf(
                //language=PostgreSQL
                statement = """
                    SELECT uuid, tilstand, journalpost_id, innsendt_tidspunkt
                    FROM  soknad_v1
                    WHERE person_ident = :ident
                """.trimIndent(),
                paramMap = mapOf(
                    "ident" to ident
                )
            ).map { row ->
                val søknadsId = UUID.fromString(row.string("uuid"))
                SøknadData(
                    søknadsId = søknadsId,
                    tilstandType = row.string("tilstand"),
                    dokumenter = hentDokumentData(søknadsId),
                    journalpostId = row.stringOrNull("journalpost_id"),
                    innsendtTidspunkt = row.zonedDateTimeOrNull("innsendt_tidspunkt")
                )
            }.asList
        )
    }

    override fun lagre(søkerOppgave: SøkerOppgave) {
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
                    "SELECT uuid, eier, soknad_data FROM SOKNAD WHERE uuid = :uuid",
                    mapOf("uuid" to søknadUUID.toString())
                ).map { row ->
                    PersistentSøkerOppgave(objectMapper.readTree(row.binaryStream("soknad_data")))
                }.asSingle
            ) ?: throw NotFoundException("Søknad med id '$søknadUUID' ikke funnet")
        }
    }

    override fun invalider(søknadUUID: UUID, eier: String): Boolean =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    "DELETE FROM SOKNAD WHERE uuid = ? AND eier = ?", søknadUUID.toString(), eier
                ).asExecute
            )
        }

    internal class PersistentSøkerOppgave(private val søknad: JsonNode) : SøkerOppgave {
        override fun søknadUUID(): UUID = UUID.fromString(søknad[SøkerOppgave.Keys.SØKNAD_UUID].asText())
        override fun eier(): String = søknad[SøkerOppgave.Keys.FØDSELSNUMMER].asText()
        override fun asFrontendformat(): JsonNode {
            søknad as ObjectNode
            val kopiAvSøknad = søknad.deepCopy()
            kopiAvSøknad.remove(SøkerOppgave.Keys.FØDSELSNUMMER)
            return kopiAvSøknad
        }

        override fun asJson(): String = søknad.toString()
    }
}

private fun SøknadData.insertQuery(personIdent: String): UpdateQueryAction {
    return queryOf(
        //language=PostgreSQL
        statement = "INSERT INTO soknad_v1(uuid,person_ident,tilstand,journalpost_id) " +
            "VALUES(:uuid,:person_ident,:tilstand,:journalpostID) ON CONFLICT(uuid) DO UPDATE " +
            "SET tilstand=:tilstand,journalpost_id=:journalpostID, innsendt_tidspunkt = :innsendtTidspunkt",
        paramMap = mapOf(
            "uuid" to søknadsId,
            "person_ident" to personIdent,
            "tilstand" to tilstandType,
            "journalpostID" to journalpostId,
            "innsendtTidspunkt" to innsendtTidspunkt
        )
    ).asUpdate
}

private fun SøknadData.insertDokumentQuery(session: Session) {
    this.dokumenter.forEach { dokumentData ->
        session.run(
            queryOf(
                //language=PostgreSQL
                statement = """
                 INSERT INTO dokument_v1(soknad_uuid, dokument_lokasjon)
                     VALUES(:uuid, :urn) ON CONFLICT (dokument_lokasjon) DO NOTHING 
                """.trimIndent(),
                paramMap = mapOf(
                    "uuid" to this.søknadsId.toString(),
                    "urn" to dokumentData.urn
                )
            ).asUpdate
        )
    }
}

private class PersonPersistenceVisitor(person: Person) : PersonVisitor {
    lateinit var ident: String
    val søknader: MutableList<SøknadData> = mutableListOf()
    lateinit var aktivitetslogg: Aktivitetslogg

    init {
        person.accept(this)
    }

    override fun visitPerson(ident: String) {
        this.ident = ident
    }

    override fun visitPerson(ident: String, søknader: List<Søknad>) {
        this.ident = ident
    }

    override fun preVisitAktivitetslogg(aktivitetslogg: Aktivitetslogg) {
        this.aktivitetslogg = aktivitetslogg
    }

    override fun visitSøknad(
        søknadId: UUID,
        person: Person,
        tilstand: Søknad.Tilstand,
        dokument: Søknad.Dokument?,
        journalpostId: String?,
        innsendtTidspunkt: ZonedDateTime?
    ) {
        søknader.add(
            SøknadData(
                søknadsId = søknadId,
                tilstandType = tilstand.tilstandType.name,
                dokumenter = dokument.toDokumentData(),
                journalpostId = journalpostId,
                innsendtTidspunkt = innsendtTidspunkt
            )
        )
    }

    private fun Søknad.Dokument?.toDokumentData(): List<SøknadData.DokumentData> {
        return this?.let { it.varianter.map { v -> SøknadData.DokumentData(urn = v.urn) } }
            ?: emptyList()
    }
}

data class PåbegyntSøknad(val uuid: UUID, val startDato: LocalDate)
