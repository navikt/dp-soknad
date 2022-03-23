package no.nav.dagpenger.soknad.db

import org.testcontainers.containers.PostgreSQLContainer

internal object Postgres {

    val instance by lazy {
        PostgreSQLContainer<Nothing>("postgres:14.2").apply {
            start()
        }
    }

    fun withMigratedDb(block: () -> Unit) {
        withCleanDb {
            PostgresDataSourceBuilder.runMigration()
            block()
        }
    }

    fun withCleanDb(block: () -> Unit) {
        System.setProperty(PostgresDataSourceBuilder.DB_HOST_KEY, instance.host)
        System.setProperty(PostgresDataSourceBuilder.DB_PORT_KEY, instance.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT).toString())
        System.setProperty(PostgresDataSourceBuilder.DB_DATABASE_KEY, instance.databaseName)
        System.setProperty(PostgresDataSourceBuilder.DB_PASSWORD_KEY, instance.password)
        System.setProperty(PostgresDataSourceBuilder.DB_USERNAME_KEY, instance.username)
        PostgresDataSourceBuilder.clean().run {
            block()
        }.also {
            System.clearProperty(PostgresDataSourceBuilder.DB_PASSWORD_KEY)
            System.clearProperty(PostgresDataSourceBuilder.DB_USERNAME_KEY)
            System.clearProperty(PostgresDataSourceBuilder.DB_HOST_KEY)
            System.clearProperty(PostgresDataSourceBuilder.DB_PORT_KEY)
            System.clearProperty(PostgresDataSourceBuilder.DB_DATABASE_KEY)
        }
    }
}
