package no.nav.dagpenger.soknad.db

import no.nav.dagpenger.soknad.db.Postgres.withCleanDb
import no.nav.dagpenger.soknad.utils.db.PostgresDataSourceBuilder.runMigration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostgresMigrationTest {
    @Test
    fun `Migration scripts are applied successfully`() {
        withCleanDb {
            val migrations = runMigration()
            assertEquals(7, migrations)
        }
    }
}
