package no.nav.dagpenger.soknad.søknad.db

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import kotliquery.Session
import kotliquery.action.UpdateQueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.serder.AktivitetsloggMapper.Companion.aktivitetslogg
import no.nav.dagpenger.soknad.serder.PersonData
import no.nav.dagpenger.soknad.serder.objectMapper
import no.nav.dagpenger.soknad.søknad.faktumflyt.SøkerOppgave
import no.nav.dagpenger.soknad.toMap
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

interface LivsyklusRepository {
    fun hent(ident: String): Person?
    fun lagre(person: Person)
    fun hentPåbegynte(personIdent: String): List<PåbegyntSøknad>
}

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
                        mapOf("ident" to personIdent, "paabegyntTilstand" to Søknad.Tilstand.Type.Påbegynt.name)
                    ).map { r ->
                        PåbegyntSøknad(UUID.fromString(r.string("uuid")), r.localDate("opprettet"))
                    }
                    ).asList
            )
        }
    }

    private fun Session.hentDokumentData(søknadId: UUID): List<PersonData.SøknadData.DokumentData> {
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
                PersonData.SøknadData.DokumentData(urn = row.string("dokument_lokasjon"))
            }.asList
        )
    }

    private fun Session.hentSøknadsData(ident: String): List<PersonData.SøknadData> {
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
                PersonData.SøknadData(
                    søknadsId = søknadsId,
                    tilstandType = row.string("tilstand"),
                    dokumenter = hentDokumentData(søknadsId),
                    journalpostId = row.stringOrNull("journalpost_id"),
                    innsendtTidspunkt = row.zonedDateTimeOrNull("innsendt_tidspunkt")
                )
            }.asList
        )
    }

    internal class PersistentSøkerOppgave(private val søknad: JsonNode) : SøkerOppgave {
        override fun søknadUUID(): UUID = UUID.fromString(søknad[SøkerOppgave.Keys.SØKNAD_UUID].asText())
        override fun eier(): String = søknad[SøkerOppgave.Keys.FØDSELSNUMMER].asText()
        override fun ferdig(): Boolean = søknad[SøkerOppgave.Keys.FERDIG].asBoolean()

        override fun asFrontendformat(): JsonNode {
            søknad as ObjectNode
            val kopiAvSøknad = søknad.deepCopy()
            kopiAvSøknad.remove(SøkerOppgave.Keys.FØDSELSNUMMER)
            return kopiAvSøknad
        }

        override fun asJson(): String = søknad.toString()
    }
}

private fun PersonData.SøknadData.insertQuery(personIdent: String): UpdateQueryAction {
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

private fun PersonData.SøknadData.insertDokumentQuery(session: Session) {
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
    val søknader: MutableList<PersonData.SøknadData> = mutableListOf()
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
            PersonData.SøknadData(
                søknadsId = søknadId,
                tilstandType = tilstand.tilstandType.name,
                dokumenter = dokument.toDokumentData(),
                journalpostId = journalpostId,
                innsendtTidspunkt = innsendtTidspunkt
            )
        )
    }

    private fun Søknad.Dokument?.toDokumentData(): List<PersonData.SøknadData.DokumentData> {
        return this?.let { it.varianter.map { v -> PersonData.SøknadData.DokumentData(urn = v.urn) } }
            ?: emptyList()
    }
}

data class PåbegyntSøknad(val uuid: UUID, val startDato: LocalDate)
