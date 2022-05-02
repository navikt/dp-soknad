package no.nav.dagpenger.soknad.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Aktivitetslogg
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.hendelse.DokumentLokasjon
import no.nav.dagpenger.soknad.serder.objectMapper
import no.nav.dagpenger.soknad.toMap
import org.postgresql.util.PGobject
import java.util.UUID
import javax.sql.DataSource

class PersonPostgresRepository(private val dataSource: DataSource) : PersonRepository {
    override fun hent(ident: String): Person? {
        val persistertIdent = using(sessionOf(dataSource)) { session ->
            //language=PostgreSQL
            session.run(
                queryOf(
                    "SELECT ident FROM person_v1 WHERE ident = :ident",
                    paramMap = mapOf("ident" to ident)
                ).map { r ->
                    r.stringOrNull("ident")
                }.asSingle
            )
        }

        return persistertIdent?.let {
            Person(persistertIdent) { person ->
                using(sessionOf(dataSource)) { session ->
                    session.run(
                        queryOf(
                            """SELECT uuid, tilstand, dokument_lokasjon, journalpost_id FROM soknad_v1 WHERE person_ident = :ident """,
                            mapOf("ident" to ident)
                        ).map { r ->
                            SøknadDB(
                                UUID.fromString(r.string("uuid")),
                                r.string("tilstand"),
                                r.stringOrNull("dokument_lokasjon"),
                                r.stringOrNull("journalpost_id")
                            )
                        }.asList

                    )
                }
                    .map { søknadDb -> søknadDb.tilSøknad(person) }
                    .toMutableList()
            }
        }
    }

    override fun lagre(person: Person) {
        val visitor = PersonPersistenceVisitor(person)
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                val internId: Long = tx.run(
                    //language=PostgreSQL
                    queryOf(
                        "INSERT INTO person_v1(ident) VALUES(:ident) ON CONFLICT DO NOTHING RETURNING id",
                        paramMap = mapOf("ident" to visitor.ident)
                    ).map { row ->
                        row.long("id")
                    }.asSingle
                )!!

                tx.run(
                    queryOf(
                        statement = """ INSERT INTO aktivitetslogg_v1 (id, data ) VALUES (:id, :data ) ON CONFLICT(id) DO UPDATE SET data = :data """.trimIndent(),
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
                    tx.run(
                        //language=PostgreSQL
                        queryOf(
                            statement = "INSERT INTO soknad_v1(uuid,person_ident,tilstand,dokument_lokasjon,journalpost_id) " +
                                "VALUES(:uuid,:person_ident,:tilstand,:dokument,:journalpostID) ON CONFLICT(uuid) DO UPDATE " +
                                "SET tilstand=:tilstand, dokument_lokasjon=:dokument, journalpost_id=:journalpostID",
                            paramMap = mapOf(
                                "uuid" to it.uuid,
                                "person_ident" to visitor.ident,
                                "tilstand" to it.tilstandType,
                                "dokument" to it.dokumentLokasjon,
                                "journalpostID" to it.journalpostId
                            )
                        ).asUpdate
                    )
                }
            }
        }
    }
}

private data class SøknadDB(
    val uuid: UUID,
    val tilstandType: String,
    val dokumentLokasjon: String?,
    val journalpostId: String?
) {
    fun tilSøknad(person: Person): Søknad {
        return Søknad.rehydrer(
            uuid,
            person,
            tilstandType,
            dokumentLokasjon,
            journalpostId
        )
    }
}

private class PersonPersistenceVisitor(person: Person) : PersonVisitor {
    lateinit var ident: String
    val søknader: MutableList<SøknadDB> = mutableListOf()
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
            SøknadDB(
                uuid = søknadId,
                tilstandType = tilstand.tilstandType.name,
                dokumentLokasjon = dokumentLokasjon,
                journalpostId = journalpostId
            )
        )
    }
}
