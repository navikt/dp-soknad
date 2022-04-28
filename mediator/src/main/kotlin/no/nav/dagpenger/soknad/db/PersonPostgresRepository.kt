package no.nav.dagpenger.soknad.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.hendelse.DokumentLokasjon
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
                tx.run(
                    //language=PostgreSQL
                    queryOf(
                        "INSERT INTO person_v1(ident) VALUES(:ident) ON CONFLICT DO NOTHING",
                        paramMap = mapOf("ident" to visitor.ident)
                    ).asUpdate
                )

                if (visitor.søknader.isNotEmpty()) {
                    tx.run(
                        queryOf(
                            "INSERT INTO soknad_v1(uuid,person_ident,tilstand,dokument_lokasjon,journalpost_id) VALUES ${visitor.søknaderValues()}"
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
    lateinit var søknader: List<Søknad>
    private val søknadSqlStrings: MutableList<String> = mutableListOf()

    init {
        person.accept(this)
    }

    override fun visitPerson(ident: String) {
        this.ident = ident
    }

    override fun visitPerson(ident: String, søknader: List<Søknad>) {
        this.ident = ident
        this.søknader = søknader
    }

    override fun visitSøknad(
        søknadId: UUID,
        person: Person,
        tilstand: Søknad.Tilstand,
        dokumentLokasjon: DokumentLokasjon?,
        journalpostId: String?
    ) {
        val lokasjon = dokumentLokasjon?.let { """'$it'""" } ?: "NULL"
        val jp = journalpostId?.let { """'$it'""" } ?: "NULL"

        søknadSqlStrings.add("""('$søknadId', '${person.ident()}', '${tilstand.tilstandType}', $lokasjon, $jp)""")
    }

    fun søknaderValues() = søknadSqlStrings.joinToString(",")
}
