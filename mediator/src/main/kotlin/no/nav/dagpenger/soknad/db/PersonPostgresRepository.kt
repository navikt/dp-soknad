package no.nav.dagpenger.soknad.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import no.nav.dagpenger.soknad.Søknad
import no.nav.dagpenger.soknad.db.PostgresDataSourceBuilder.dataSource
import javax.sql.DataSource

class PersonPostgresRepository(private val dataSource: DataSource) : PersonRepository {
    override fun hent(ident: String): Person? {
        val ident = using(sessionOf(dataSource)) { session ->
            //language=PostgreSQL
            session.run(
                queryOf(
                    "SELECT ident FROM person_v1 where ident = :ident",
                    paramMap = mapOf("ident" to ident)
                ).map { r ->
                    r.stringOrNull("ident")
                }.asSingle
            )
        }

        return ident?.let {
            Person(ident) { mutableListOf() }
        }
    }

    override fun lagre(person: Person) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                //language=PostgreSQL
                queryOf(
                    "INSERT INTO person_v1(ident) VALUES(:ident)",
                    paramMap = mapOf("ident" to PersonPersistenceVisitor(person).ident)
                ).asUpdate
            )
        }
    }
}

internal class PersonPersistenceVisitor(person: Person) : PersonVisitor {
    lateinit var ident: String
    lateinit var søknader: List<Søknad>

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
}
