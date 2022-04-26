package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.Person
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PersonPostgresRepositoryTest {
    @Test
    fun `Lagre og hente person`() {
        withMigratedDb {
            PersonPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                it.lagre(Person("12345678910"))
                val person = it.hent("12345678910")

                assertNotNull(person)
                assertEquals("12345678910", PersonPersistenceVisitor(person!!).ident)
            }
        }
    }

    @Test
    fun `Hent perosn som ikke finnes`() {
        withMigratedDb {
            PersonPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                assertNull(it.hent("finnes ikke"))
            }
        }
    }
}
