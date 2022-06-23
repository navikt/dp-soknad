package no.nav.dagpenger.søknad.db

import no.nav.dagpenger.søknad.db.Postgres.withCleanDb
import no.nav.dagpenger.søknad.utils.db.PostgresDataSourceBuilder.runMigration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PostgresMigrationTest {
    @Test
    fun `Migration scripts are applied successfully`() {
        withCleanDb {
            val migrations = runMigration()
            assertEquals(4, migrations)
        }
    }
}
