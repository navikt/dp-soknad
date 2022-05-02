package no.nav.dagpenger.soknad.db

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SøknadMalRepositoryTest {

    @Test
    fun `søknadmal skal lagres`() {
        withMigratedDb {
            SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource).let {
                val expectedMal = SøknadMal("prosessnavn", 123, jacksonObjectMapper().createObjectNode())
                it.lagre(expectedMal)
                assertAntallMalerIBasen(1)

                it.lagre(expectedMal)
                assertAntallMalerIBasen(1)

                val versjon2 = expectedMal.copy(prosessversjon = 456)
                it.lagre(versjon2)
                assertAntallMalerIBasen(2)
            }
        }
    }

    private fun assertAntallMalerIBasen(antall: Int) {
        using(sessionOf(PostgresDataSourceBuilder.dataSource)) { session ->
            session.run(
                queryOf("SELECT COUNT(*) FROM soknadmal").map {
                    assertEquals(antall, it.int(1))
                }.asSingle
            )
        }
    }
}
