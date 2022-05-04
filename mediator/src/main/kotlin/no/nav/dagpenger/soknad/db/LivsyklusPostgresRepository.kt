package no.nav.dagpenger.soknad.db

import kotliquery.action.UpdateQueryAction
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.Søknad.Tilstand.Type.Påbegynt
import no.nav.dagpenger.soknad.hendelse.DokumentLokasjon
import no.nav.dagpenger.soknad.serder.AktivitetsloggMapper.Companion.aktivitetslogg
import no.nav.dagpenger.soknad.serder.PersonData
import no.nav.dagpenger.soknad.serder.PersonData.SøknadData
import no.nav.dagpenger.soknad.serder.objectMapper
import no.nav.dagpenger.soknad.toMap
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class LivsyklusPostgresRepository(private val dataSource: DataSource) : LivsyklusRepository {

    override fun hent(ident: String): Person? {
        val personData: PersonData? = using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    hentAktivitetsloggOgPersonQuery,
                    paramMap = mapOf("ident" to ident)
                ).map { r ->
                    r.longOrNull("person_id")?.let {
                        PersonData(
                            internId = it,
                            ident = r.string("person_ident"),
                            aktivitetsLogg = r.binaryStream("aktivitetslogg").aktivitetslogg()
                        )
                    }
                }.asSingle
            )
        }

        return personData?.also { person ->
            person.søknader = using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        //language=PostgreSQL
                        """SELECT uuid, tilstand, dokument_lokasjon, journalpost_id FROM soknad_v1 WHERE person_ident = :ident """,
                        mapOf("ident" to person.ident)
                    ).map { r ->
                        SøknadData(
                            UUID.fromString(r.string("uuid")),
                            r.string("tilstand"),
                            r.stringOrNull("dokument_lokasjon"),
                            r.stringOrNull("journalpost_id")
                        )
                    }.asList
                )
            }
        }?.createPerson()
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
}

private fun SøknadData.insertQuery(personIdent: String): UpdateQueryAction =
    queryOf(
        //language=PostgreSQL
        statement = "INSERT INTO soknad_v1(uuid,person_ident,tilstand,dokument_lokasjon,journalpost_id) " +
            "VALUES(:uuid,:person_ident,:tilstand,:dokument,:journalpostID) ON CONFLICT(uuid) DO UPDATE " +
            "SET tilstand=:tilstand, dokument_lokasjon=:dokument, journalpost_id=:journalpostID",
        paramMap = mapOf(
            "uuid" to søknadsId,
            "person_ident" to personIdent,
            "tilstand" to tilstandType,
            "dokument" to dokumentLokasjon,
            "journalpostID" to journalpostId
        )
    ).asUpdate

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
        dokumentLokasjon: DokumentLokasjon?,
        journalpostId: String?
    ) {
        søknader.add(
            SøknadData(
                søknadsId = søknadId,
                tilstandType = tilstand.tilstandType.name,
                dokumentLokasjon = dokumentLokasjon,
                journalpostId = journalpostId
            )
        )
    }
}

data class PåbegyntSøknad(val uuid: UUID, val startDato: LocalDate)

private val hentAktivitetsloggOgPersonQuery =
    //language=PostgreSQL
    """
            SELECT p.id AS person_id, p.ident AS person_ident, a.data AS aktivitetslogg 
            FROM person_v1 AS p, aktivitetslogg_v1 AS a 
            WHERE p.id=a.id AND p.ident=:ident
    """.trimIndent()
