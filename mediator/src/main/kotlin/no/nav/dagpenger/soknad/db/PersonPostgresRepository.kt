package no.nav.dagpenger.soknad.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.PersonVisitor
import javax.sql.DataSource

class PersonPostgresRepository(private val dataSource: DataSource) : PersonRepository {
    override fun hent(ident: String): Person? {
        return using(sessionOf(dataSource)) { session ->
            //language=PostgreSQL
            session.run(
                queryOf(
                    "SELECT ident FROM person_v1 where ident = :ident",
                    paramMap = mapOf("ident" to ident)
                ).map { r ->
                    r.stringOrNull(1)?.let { Person(it) }
                }.asSingle
            )
        }
    }

    override fun lagre(person: Person) {
        val i = using(sessionOf(dataSource)) { session ->
            session.run(
                //language=PostgreSQL
                queryOf(
                    "INSERT INTO person_v1(ident) VALUES(:ident)",
                    paramMap = mapOf("ident" to PersonPersistenceVisitor(person).ident)
                ).asUpdate
            )
        }
        print(i)
    }
}

internal class PersonPersistenceVisitor(person: Person) : PersonVisitor {
    lateinit var ident: String

    init {
        person.accept(this)
    }

    override fun visitPerson(ident: String) {
        this.ident = ident
    }
}
