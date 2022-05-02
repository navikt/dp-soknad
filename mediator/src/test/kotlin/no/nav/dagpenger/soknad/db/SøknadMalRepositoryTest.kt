package no.nav.dagpenger.soknad.db

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.soknad.db.Postgres.withMigratedDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SøknadMalRepositoryTest {

    @Test
    fun `søknadmal skal lagres`() {
        withMigratedDb {
            SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource).let { repo ->
                val expectedMal = SøknadMal("prosessnavn", 123, jacksonObjectMapper().createObjectNode())
                repo.lagre(expectedMal)
                assertAntallMalerIBasen(1)

                repo.lagre(expectedMal)
                assertAntallMalerIBasen(1)

                val versjon2 = expectedMal.copy(prosessversjon = 456)
                repo.lagre(versjon2)
                assertAntallMalerIBasen(2)
            }
        }
    }

    @Test
    fun `Hente ut søknadsmal fra basen`() {
        withMigratedDb {
            SøknadMalPostgresRepository(PostgresDataSourceBuilder.dataSource).let { repo ->
                val prosessnavn = "Dagpenger"
                val førsteMal = SøknadMal(prosessnavn, 123, jacksonObjectMapper().createObjectNode())

                assertThrows<IngenMalFunnetException> { repo.hentNyesteMal(prosessnavn) }

                repo.lagre(førsteMal)
                val hentetFørsteMal = repo.hentNyesteMal(prosessnavn)
                assertEquals(førsteMal, hentetFørsteMal)

                val andreMal = førsteMal.copy(prosessversjon = 789)
                repo.lagre(andreMal)
                val hentetAndreMal = repo.hentNyesteMal(prosessnavn)
                assertEquals(andreMal, hentetAndreMal)
                assertNotEquals(førsteMal, hentetAndreMal)
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
