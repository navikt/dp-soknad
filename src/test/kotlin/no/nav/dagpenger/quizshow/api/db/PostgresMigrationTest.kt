package no.nav.dagpenger.quizshow.api.db

import no.nav.dagpenger.quizshow.api.db.Postgres.withCleanDb
import no.nav.dagpenger.quizshow.api.db.PostgresDataSourceBuilder.runMigration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostgresMigrationTest {
    @Test
    fun `Migration scripts are applied successfully`() {
        withCleanDb {
            val migrations = runMigration()
            assertEquals(2, migrations)
        }
    }
}
